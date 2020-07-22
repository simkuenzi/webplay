package com.github.simkuenzi.webplay;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.nio.file.StandardWatchEventKinds.*;

public class Recorder {

    public static void main(String[] args) throws Exception {

        Recorder recorder = new Recorder(Paths.get("scenario.xml"), 9033, 9000,
                Collections.singletonList("text/html"), Path.of("stop"));

        recorder.record();
    }

    private final Path outputFile;
    private final int port;
    private final int portOfApp;
    private final List<String> includedContentTypes;
    private final Path stopFile;

    public Recorder(Path outputFile, int port, int portOfApp, List<String> includedContentTypes, Path stopFile) {
        this.outputFile = outputFile;
        this.port = port;
        this.portOfApp = portOfApp;
        this.includedContentTypes = includedContentTypes;
        this.stopFile = stopFile;
    }

    private volatile boolean running;


    public void record() throws Exception {
        running = true;
        Thread recordThread = recordThread();
        recordThread.start();

        WatchService watchService = FileSystems.getDefault().newWatchService();
        stopFile.toAbsolutePath().getParent().register(watchService, ENTRY_MODIFY);

        while (running) {
            final WatchKey wk = watchService.take();
            for (WatchEvent<?> event : wk.pollEvents()) {
                final Path changed = (Path) event.context();
                if (changed.getFileName().equals(stopFile.getFileName())) {
                    running = false;
                } else {
                    wk.reset();
                }
            }
        }

        recordThread.interrupt();
        recordThread.join();
    }

    private Thread recordThread() {
        return new Thread(null, () -> {
            try {
                ServerSocketChannel serverSocket = ServerSocketChannel.open();
                serverSocket.bind(new InetSocketAddress(port));
                System.out.printf("Recording on http://localhost:%d%n", port);
                SocketChannel clientSocket;
                try {
                    clientSocket = serverSocket.accept();
                } catch (ClosedByInterruptException e) {
                    // End without having done anything
                    return;
                }

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (Writer out = new OutputStreamWriter(buffer)) {
                    RequestBuilder requestBuilder = new XmlTestScenario(out);


//                    ServerSocket serverSocket = new ServerSocket(9011);

                     AssertionBuilder assertionBuilder = null;

//                    Socket clientSocket = serverSocket.accept();

                    running = true;
                    while (running) {
                        try {
                            SocketChannel appSocket = SocketChannel.open(new InetSocketAddress("localhost", portOfApp));
//                            Socket appSocket = new Socket(InetAddress.getLocalHost(), 9000);
                            ClientToApp clientToApp = transfer(clientSocket, appSocket, requestBuilder,
                                    (builder, urlPath, method, headers, payload, mime) ->
                                            new ClientToApp(builder, method, urlPath, headers, payload));
                            Optional<AssertionBuilder> ab = transfer(appSocket, clientSocket, clientToApp,
                                    (builder, urlPath, method, headers, payload, mime) ->
                                            clientToApp.request(payload, mime));
                            if (ab.isPresent()) {
                                assertionBuilder = ab.get();
                                requestBuilder = ab.get();
                            }

                        } catch (InterruptedException | ClosedByInterruptException e) {
                            // Let the thread end...
                        }
                    }

                    if (assertionBuilder != null) {
                        assertionBuilder.end();
                    }
                }

                // Pretty print
                try (Writer writer = Files.newBufferedWriter(outputFile)) {
                    Transformer transformer = TransformerFactory.newInstance().newTransformer();
                    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                    StreamResult result = new StreamResult(writer);
                    StreamSource source = new StreamSource(new ByteArrayInputStream(buffer.toByteArray()));
                    transformer.transform(source, result);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            running = false;
        }, "Recorder");
    }

    public void stop() {

    }


    private static final Pattern REQUEST_LINE_PATTERN = Pattern.compile("(\\H+)\\h*(\\H+).*");
    private static final Pattern HEADER_PATTERN = Pattern.compile("(\\H+)\\h*:\\h*(.+)");
    private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("(\\H+?)(:?\\h*;\\h*(\\H+)=(\\H+))*");
    private static final int HEADER_END_SEQ = 0x0d0a0d0a;

    interface BuildAction<I, O> {
        O build(I builder, String urlPath, String method, Map<String, String> headers, String payload, String mime) throws Exception;
    }

    private <I, O> O transfer(SocketChannel in, SocketChannel out, I requestBuilder, BuildAction<I, O> buildAction) throws Exception {
        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
        int endSeq = 0;
        ByteBuffer singleByte = ByteBuffer.allocate(1);
        do {
            in.read(singleByte);
            endSeq = (endSeq << 8) + singleByte.get(0);
            headerBuffer.write(singleByte.get(0));
            out.write(ByteBuffer.wrap(singleByte.array()));
            singleByte.clear();
        } while (endSeq != HEADER_END_SEQ);

        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(headerBuffer.toByteArray())));

        String method;
        String urlPath;
        String requestLine = reader.readLine();
        Matcher requestLineMatcher = REQUEST_LINE_PATTERN.matcher(requestLine);
        if (requestLineMatcher.matches()) {
            method = requestLineMatcher.group(1);
            urlPath = requestLineMatcher.group(2);
        } else {
            throw new Exception(String.format("Request line %s is not understood.", requestLine));
        }

        Map<String, String> headers = new HashMap<>();
        String headerLine;
        while ((headerLine = reader.readLine()) != null) {
            if (!headerLine.isEmpty()) {
                Matcher matcher = HEADER_PATTERN.matcher(headerLine);
                if (matcher.matches()) {
                    headers.put(matcher.group(1), matcher.group(2));
                } else {
                    throw new Exception(String.format("Header line %s is not understood.", headerLine));
                }
            }
        }


        int contentLength = headers.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().equals("content-length"))
                .findFirst()
                .map(e -> Integer.parseInt(e.getValue()))
                .orElse(0);

        String payloadText;
        if (contentLength > 0) {
            ByteBuffer payload = ByteBuffer.allocate(contentLength);
            in.read(payload);
            payloadText = new String(payload.array());
            out.write(ByteBuffer.wrap(payload.array()));
        } else {
            payloadText = "";
        }

        String contentType = headers.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().equals("content-type"))
                .findFirst().map(Map.Entry::getValue).orElse("");

        String mime;
        String charset;
        Matcher matcher = CONTENT_TYPE_PATTERN.matcher(contentType);
        if (matcher.matches()) {
            mime = matcher.group(1);
            charset = IntStream.range(2, matcher.groupCount() - 1)
                    .filter(i -> matcher.group(i) != null)
                    .filter(i -> matcher.group(i).equals("charset"))
                    .mapToObj(i -> matcher.group(i + 1))
                    .findFirst().orElse("");
        } else {
            mime = "";
            charset = "";
        }

        return buildAction.build(requestBuilder, urlPath, method, headers, payloadText, mime);
    }

    private class ClientToApp  {
        private final RequestBuilder requestBuilder;
        private final String method;
        private final String urlPath;
        private final Map<String, String> headers;
        private final String payload;

        public ClientToApp(RequestBuilder requestBuilder, String method, String urlPath, Map<String, String> headers, String payload) {
            this.requestBuilder = requestBuilder;
            this.method = method;
            this.urlPath = urlPath;
            this.headers = headers;
            this.payload = payload;
        }

        public Optional<AssertionBuilder> request(String serverPayload, String serverMime) throws XMLStreamException {
            if (includedContentTypes.contains(serverMime)) {
                AssertionBuilder assertionBuilder = requestBuilder.request(urlPath, method, headers, payload);
                for (Element input : Jsoup.parse(serverPayload).select("input")) {
                    assertionBuilder = assertionBuilder.assertion(input.val(), String.format("input[name=%s]", input.attr("name")));
                }
                return Optional.of(assertionBuilder);
            }
            return Optional.empty();
        }
    }
}

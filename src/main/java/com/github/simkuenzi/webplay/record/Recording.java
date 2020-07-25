package com.github.simkuenzi.webplay.record;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class Recording implements AutoCloseable {
    private static final Pattern REQUEST_LINE_PATTERN = Pattern.compile("(\\H+)\\h*(\\H+).*");
    private static final Pattern HEADER_PATTERN = Pattern.compile("(\\H+)\\h*:\\h*(.+)");
    private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("(\\H+?)(:?\\h*;\\h*(\\H+)=(\\H+))*");
    private static final int HEADER_END_SEQ = 0x0d0a0d0a;

    private final ServerSocketChannel serverSocket;

    private Thread watcher;
    private volatile boolean running = true;
    private final CountDownLatch startupWaitHandle = new CountDownLatch(1);

    public Recording(ServerSocketChannel serverSocket) {
        this.serverSocket = serverSocket;
    }

    public synchronized void run(int portOfApp, Path outputFile, List<String> includedContentTypes, Path stopFile) throws Exception {
        try (serverSocket) {
            if (watcher != null) {
                throw new IllegalStateException("Method 'run' can only entered once.");
            }

            Thread watchedThread = Thread.currentThread();
            watcher = new Thread(() -> watch(watchedThread, stopFile));
            watcher.setDaemon(true);
            watcher.start();

            while (running) {
                acceptConnection(serverSocket, portOfApp, outputFile, includedContentTypes);
            }
        }
    }

    public void stop() throws Exception {
        startupWaitHandle.await();
        watcher.interrupt();
        watcher.join();
    }

    @Override
    public void close() throws Exception {
        stop();
    }

    private void acceptConnection(ServerSocketChannel serverSocket, int portOfApp, Path outputFile, List<String> includedContentTypes) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (Writer out = new OutputStreamWriter(buffer)) {
            RequestBuilder requestBuilder = new XmlTest(out).test();
            startupWaitHandle.countDown();
            try (SocketChannel clientSocket = serverSocket.accept()) {
                while (running) {
                    try (SocketChannel appSocket = SocketChannel.open(new InetSocketAddress("localhost", portOfApp))) {
                        ClientToApp clientToApp = transfer(clientSocket, appSocket, requestBuilder,
                                (builder, urlPath, method, headers, payload, mime) ->
                                        new ClientToApp(builder, method, urlPath, headers, payload, mime));
                        requestBuilder = transfer(appSocket, clientSocket, clientToApp,
                                (builder, urlPath, method, headers, payload, mime) ->
                                        clientToApp.request(payload, mime, includedContentTypes));
                    }
                }
            } catch (InterruptedException | ClosedByInterruptException e) {
                // End thread nicely
            }

            requestBuilder.end();
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

        String contentType = headers.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().equals("content-type"))
                .findFirst().map(Map.Entry::getValue).orElse("");

        String mime;
        String charset;
        Matcher matcher = CONTENT_TYPE_PATTERN.matcher(contentType);
        Charset utf8 = StandardCharsets.UTF_8;

        if (matcher.matches()) {
            mime = matcher.group(1);
            charset = IntStream.range(2, matcher.groupCount() - 1)
                    .filter(i -> matcher.group(i) != null)
                    .filter(i -> matcher.group(i).equals("charset"))
                    .mapToObj(i -> matcher.group(i + 1))
                    .findFirst().orElse(utf8.name());
        } else {
            mime = "";
            charset = utf8.name();
        }

        String payloadText;
        if (contentLength > 0) {
            ByteBuffer payload = ByteBuffer.allocate(contentLength);
            in.read(payload);
            payloadText = new String(payload.array(), charset);
            out.write(ByteBuffer.wrap(payload.array()));
        } else {
            payloadText = "";
        }

        return buildAction.build(requestBuilder, urlPath, method, headers, payloadText, mime);
    }

    private static class ClientToApp {
        private final RequestBuilder requestBuilder;
        private final String method;
        private final String urlPath;
        private final Map<String, String> headers;
        private final String payload;
        private final String mime;

        public ClientToApp(RequestBuilder requestBuilder, String method, String urlPath, Map<String, String> headers, String payload, String mime) {
            this.requestBuilder = requestBuilder;
            this.method = method;
            this.urlPath = urlPath;
            this.headers = headers;
            this.payload = payload;
            this.mime = mime;
        }

        public RequestBuilder request(String serverPayload, String serverMime, List<String> includedContentTypes) throws Exception {
            if (includedContentTypes.contains(serverMime) || includedContentTypes.contains(mime)) {
                AssertionBuilder assertionBuilder = requestBuilder.request(urlPath, method, headers, payload);
                Document document = Jsoup.parse(serverPayload);
                for (Element input : document.select("input")) {
                    assertionBuilder = assertionBuilder.assertion("value", input.val(), String.format("input[name=%s]", input.attr("name")));
                }
                for (Element textarea : document.select("textarea")) {
                    assertionBuilder = assertionBuilder.assertion(textarea.text(), String.format("textarea[name=%s]", textarea.attr("name")));
                }
                return assertionBuilder;
            }
            return requestBuilder;
        }
    }

    private void watch(Thread watched, Path stopFile) {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            stopFile.toAbsolutePath().getParent().register(watchService, ENTRY_MODIFY);
            while (running) {
                final WatchKey wk = watchService.take();
                for (WatchEvent<?> event : wk.pollEvents()) {
                    final Path changed = (Path) event.context();
                    if (changed.getFileName().equals(stopFile.getFileName())) {
                        // End recording on stopFile event
                        running = false;
                        break;
                    } else {
                        wk.reset();
                    }
                }
            }
        } catch (ClosedWatchServiceException | InterruptedException e) {
            // When watcher is interrupted, the recorder thread ends too.
            running = false;
        } catch (Exception e) {
            // Abnormal end of watcher.
            e.printStackTrace();
            // End recorder thread
            running = false;
        }

        // End recorder thread.
        System.out.println("Stopping recorder");
        watched.interrupt();
        try {
            watched.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Recorder stopped");
    }


    interface BuildAction<I, O> {
        O build(I builder, String urlPath, String method, Map<String, String> headers, String payload, String mime) throws Exception;
    }
}

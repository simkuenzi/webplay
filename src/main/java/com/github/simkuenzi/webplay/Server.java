package com.github.simkuenzi.webplay;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {


    public static void main(String[] args) throws IOException, InterruptedException {

        try (Writer out = new FileWriter("scenario.xml")) {
            TestScenario testScenario = new XmlTestScenario(out);

            final boolean[] run = {true};

            Thread t = new Thread(null, () -> {
                try {
                    ServerSocket serverSocket = new ServerSocket(9011);
                    RequestBuilder requestBuilder = testScenario;
                    Request request = null;
                    Socket clientSocket = serverSocket.accept();

                    while (run[0]) {
                        Socket appSocket = new Socket(InetAddress.getLocalHost(), 9000);

                        System.out.println("Client -> App");
//                        transfer(clientSocket.getInputStream(), appSocket.getOutputStream());
                        request = transfer(clientSocket.getInputStream(), appSocket.getOutputStream(), requestBuilder,
                                RequestBuilder::request);
                        System.out.println("App -> Client");
//                        transfer(appSocket.getInputStream(), clientSocket.getOutputStream());
                        requestBuilder = transfer(appSocket.getInputStream(), clientSocket.getOutputStream(), request,
                                (builder, urlPath, method, headers, payload) -> {
                                    Assertion a = null;
                                    AssertionBuilder ab = builder;
                                    for (Element input : Jsoup.parse(payload).select("input")) {
                                        a = ab.assertion(input.val(), String.format("input[name=%s]", input.attr("name")));
                                        ab = a;
                                    }
                                    return a != null ? a : builder;
                                });
                    }

                    if (request != null) {
                        request.end();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, "Recorder");
            t.start();

            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
            console.readLine();
            run[0] = false;
            t.join();

        }


    }

    private static final Pattern REQUEST_LINE_PATTERN = Pattern.compile("(\\H+)\\h*(\\H+).*");
    private static final Pattern HEADER_PATTERN = Pattern.compile("(\\H+)\\h*:\\h*(.+)");
    private static final int HEADER_END_SEQ = 0x0d0a0d0a;

    interface BuildAction<I, O> {
        O build(I builder, String urlPath, String method, Map<String, String> headers, String payload) throws Exception;
    }

    private static void transfer(InputStream in, OutputStream out) throws Exception {
        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
        int endSeq = 0;
        do {
            int b = in.read();
            endSeq = (endSeq << 8) + (byte) b;
            headerBuffer.write(b);
            out.write(b);
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
            System.out.println(headerLine);
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
            System.out.println("Read payload of length " + contentLength);
            byte[] payload = new byte[contentLength];
            in.read(payload);
            payloadText = new String(payload);
            out.write(payload);
        } else {
            payloadText = "";
        }

        boolean isHtml = headers.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().equals("content-length"))
                .findFirst()
                .map(e -> e.getValue().startsWith("text/html"))
                .orElse(false);

    }

    private static <I, O> O transfer(InputStream in, OutputStream out, I requestBuilder, BuildAction<I, O> buildAction) throws Exception {
        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
        int endSeq = 0;
        do {
            int b = in.read();
            endSeq = (endSeq << 8) + (byte) b;
            headerBuffer.write(b);
            out.write(b);
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
            System.out.println(headerLine);
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
            System.out.println("Read payload of length " + contentLength);
            byte[] payload = new byte[contentLength];
            in.read(payload);
            payloadText = new String(payload);
            out.write(payload);
        } else {
            payloadText = "";
        }

        boolean isHtml = headers.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().equals("content-type"))
                .findFirst()
                .map(e -> e.getValue().startsWith("text/html"))
                .orElse(false);

        return buildAction.build(requestBuilder, urlPath, method, headers, isHtml ? payloadText : "");
    }
}

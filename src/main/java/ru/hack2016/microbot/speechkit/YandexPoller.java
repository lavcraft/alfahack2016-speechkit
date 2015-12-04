package ru.hack2016.microbot.speechkit;

import com.google.protobuf.ByteString;
import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.util.Args;
import ru.alfabank.Voiceproxy;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;

/**
 * Created by aleksandr on 04.12.15.
 */
public class YandexPoller {
    public String process(byte[] audioData) throws IOException {
        StringBuilder result = new StringBuilder();

        String yandexKey = System.getenv("YANDEX_SPEECH_KEY");

        HttpGet httpGet = new HttpGet("https://asr.yandex.net/asr_partial");
        httpGet.setHeader("Upgrade", "dictation");

        RequestConfig requestConfig = RequestConfig.copy(RequestConfig.DEFAULT)
                //.setSocketTimeout(Integer.MAX_VALUE)
                .build();

        HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                //.setKeepAliveStrategy((response, context) -> Long.MAX_VALUE)
                .setRequestExecutor(new HttpRequestExecutor() {
                    @Override
                    protected HttpResponse doReceiveResponse(HttpRequest request, HttpClientConnection conn, HttpContext context) throws HttpException, IOException {
                        Args.notNull(request, "HTTP request");
                        Args.notNull(conn, "Client connection");
                        Args.notNull(context, "HTTP context");
                        HttpResponse response = null;
                        int statusCode = 0;

                        while (response == null) {

                            response = conn.receiveResponseHeader();
                            if (canResponseHaveBody(request, response)) {
                                conn.receiveResponseEntity(response);
                            }
                            statusCode = response.getStatusLine().getStatusCode();

                        } // while intermediate response

                        if (statusCode < HttpStatus.SC_OK) {
                            ManagedHttpClientConnection currentConnection = (ManagedHttpClientConnection) conn;
                            if (currentConnection.isOpen()) {
                                Socket socket = currentConnection.getSocket();

                                Voiceproxy.ConnectionRequest connectionRequest = Voiceproxy.ConnectionRequest.newBuilder()
                                        .setSpeechkitVersion("")
                                        .setServiceName("asr_dictation")
                                        .setUuid("01ae13cb744628b58fb536d496daa1e1")
                                        .setApiKey(yandexKey)
                                        .setApplicationName("my-test-app")
                                        .setDevice("mac")
                                        .setCoords("55.75,37.6")
                                        .setTopic("notes")
                                        .setLang("ru-RU")
                                        .setFormat("audio/x-pcm;bit=16;rate=16000")
                                        .build();

                                byte[] protoRequest = connectionRequest.toByteArray();
                                byte[] hexSizeOfRequest = Integer.toHexString(protoRequest.length).getBytes();
                                byte[] colon = "\r\n".getBytes();
                                socket.getOutputStream().write(hexSizeOfRequest);
                                socket.getOutputStream().write(colon);
                                socket.getOutputStream().write(protoRequest);
                                socket.getOutputStream().flush();
                                byte[] readBuffer = new byte[10000];
                                socket.getInputStream().read(readBuffer);

                                int colonStartPosition = 0;
                                while (colonStartPosition < readBuffer.length) {
                                    byte[] candidate = Arrays.copyOfRange(readBuffer, colonStartPosition, colonStartPosition + 2);
                                    if (Arrays.equals(candidate, colon)) {
                                        break;
                                    }
                                    colonStartPosition = colonStartPosition + 2;
                                }
                                int responseSize = Integer.parseInt(new String(Arrays.copyOfRange(readBuffer, 0, colonStartPosition)), 16);
                                Voiceproxy.ConnectionResponse connectionResponse = Voiceproxy.ConnectionResponse.parseFrom(Arrays.copyOfRange(readBuffer, colonStartPosition + 2, colonStartPosition + 2 + responseSize));

                                result.append(processData(socket, audioData));
                            }
                        }

                        return response;
                    }
                })
                .build()
                .execute(httpGet);
        return result.toString();
    }

    private String processData(Socket messageSocket, byte[] audioData) throws IOException {
        byte[] protoRequest;
        byte[] hexSizeOfRequest;
        byte[] colon;
        byte[] readBuffer;
        int colonStartPosition;

        StringBuilder result = new StringBuilder();

        int responseSize;Voiceproxy.AddData data = Voiceproxy.AddData.newBuilder()
                .setAudioData(ByteString.copyFrom(audioData))
                .setLastChunk(false)
                .build();

        protoRequest = data.toByteArray();
        hexSizeOfRequest = Integer.toHexString(protoRequest.length).getBytes();
        colon = "\r\n".getBytes();
        messageSocket.getOutputStream().write(hexSizeOfRequest);
        messageSocket.getOutputStream().write(colon);
        messageSocket.getOutputStream().write(protoRequest);
        messageSocket.getOutputStream().flush();

        readBuffer = new byte[10000];
        messageSocket.getInputStream().read(readBuffer);

        colonStartPosition = 0;
        while (colonStartPosition < readBuffer.length) {
            if (colon[0] == readBuffer[colonStartPosition]) {
                break;
            }

            colonStartPosition++;
        }
        responseSize = Integer.parseInt(new String(Arrays.copyOfRange(readBuffer, 0, colonStartPosition)), 16);
        Voiceproxy.AddDataResponse addDataResponse = Voiceproxy.AddDataResponse.parseFrom(Arrays.copyOfRange(readBuffer, colonStartPosition + 2, colonStartPosition + 2 + responseSize));

        addDataResponse.getRecognitionList().stream().forEach(recognition ->
                recognition.getWordsList().forEach(word ->
                     result.append(word.getValue() + " ")
                )
        );

        return result.toString();
    }
}

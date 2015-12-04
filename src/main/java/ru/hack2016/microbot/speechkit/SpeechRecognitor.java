package ru.hack2016.microbot.speechkit;

import com.google.protobuf.ByteString;
import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.util.Args;
import ru.alfabank.Voiceproxy;
import rx.Observable;
import rx.Subscriber;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.Optional;

/**
 * Created by aleksandr on 04.12.15.
 */
public class SpeechRecognitor {
    AudioFormat format;
    TargetDataLine targetDataLine = null;
    ByteArrayOutputStream output = null;
    boolean stopCapture = false;

    int sampleLength;

    public SpeechRecognitor(int sampleLength) {
        this.sampleLength = sampleLength;
        try {
            format = initAudioFormat();
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);

            targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
            targetDataLine.open(format);
            targetDataLine.start();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private AudioFormat initAudioFormat() {
        float sampleRate = 16000;
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed,
                bigEndian);
    }

    private void startRecord() {
        new CaptureThread().start();
    }

    private void stopRecord() {
        stopCapture = true;
    }

    public Observable<String> recognize() {
        final Observable<String> recognizePoller = Observable.create(
                new Observable.OnSubscribe<String>() {

                    @Override
                    public void call(Subscriber<? super String> sub) {
                        byte[] audioData;

                        try {
                            startRecord();
                            //while (true) {
                                Thread.sleep(sampleLength);
                                stopRecord();
                                audioData = output.toByteArray();
                            //    startRecord();
                                sub.onNext(sendToYandex(audioData));
                            //}
                        } catch (Exception e) {
                            sub.onError(e);
                        } finally {
                            sub.onCompleted();
                        }
                    }

                    private String sendToYandex(byte[] audioData) throws IOException {

                        String yandexKey = System.getenv("YANDEX_SPEECH_KEY");

                        HttpGet httpGet = new HttpGet("https://asr.yandex.net/asr_partial");
                        httpGet.setHeader("Upgrade", "dictation");

                        RequestConfig requestConfig = RequestConfig.copy(RequestConfig.DEFAULT)
                                .setExpectContinueEnabled(true)
                                .build();

                        StringBuilder result = new StringBuilder();

                        CloseableHttpResponse response = HttpClients.custom()
                                .setDefaultRequestConfig(requestConfig)
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
                                                        .setUuid("01ae13cb744628b58fb536d496daa1e6")
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

                                                System.out.println(connectionResponse);

                                                Voiceproxy.AddData data = Voiceproxy.AddData.newBuilder()
                                                        .setAudioData(ByteString.copyFrom(audioData))
                                                        .setLastChunk(false)
                                                        .build();

                                                protoRequest = data.toByteArray();
                                                hexSizeOfRequest = Integer.toHexString(protoRequest.length).getBytes();
                                                colon = "\r\n".getBytes();
                                                socket.getOutputStream().write(hexSizeOfRequest);
                                                socket.getOutputStream().write(colon);
                                                socket.getOutputStream().write(protoRequest);
                                                socket.getOutputStream().flush();

                                                readBuffer = new byte[10000];
                                                socket.getInputStream().read(readBuffer);

                                                colonStartPosition = 0;
                                                while (colonStartPosition < readBuffer.length) {
                                                    if (colon[0] == readBuffer[colonStartPosition]) {
                                                        break;
                                                    }

                                                    colonStartPosition++;
                                                }
                                                responseSize = Integer.parseInt(new String(Arrays.copyOfRange(readBuffer, 0, colonStartPosition)), 16);
                                                Voiceproxy.AddDataResponse addDataResponse = Voiceproxy.AddDataResponse.parseFrom(Arrays.copyOfRange(readBuffer, colonStartPosition + 2, colonStartPosition + 2 + responseSize));

                                                Optional<Voiceproxy.Result> bestRecognition = addDataResponse.getRecognitionList().stream().max((r1, r2) ->
                                                                Float.compare(r2.getConfidence(), r1.getConfidence())
                                                );

                                                if (bestRecognition.isPresent()) {
                                                    bestRecognition.get().getWordsList().forEach(word ->
                                                            result.append(word.getValue() + " ")
                                                    );
                                                }
                                            }
                                        }

                                        return response;
                                    }
                                })
                                .build()
                                .execute(httpGet);

                        return result.toString();
                    }
                }
        );

        return recognizePoller;
    }

    private class CaptureThread extends Thread {
        byte data[] = new byte[100000];

        public void run() {
            output = new ByteArrayOutputStream();
            stopCapture = false;

            try {
                do {
                    int count = targetDataLine.read(data, 0, data.length);
                    if (count > 0) {
                        output.write(data, 0, count);
                    }
                } while (!stopCapture);
                output.close();
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }
}

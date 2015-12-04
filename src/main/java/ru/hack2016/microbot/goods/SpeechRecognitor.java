package ru.hack2016.microbot.goods;

import rx.Observable;
import rx.Subscriber;

import javax.net.ssl.HttpsURLConnection;
import javax.sound.sampled.*;
import java.io.*;
import java.net.URL;

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
                        try {
                            startRecord();
                            Thread.sleep(sampleLength);
                            stopRecord();

                            sub.onNext(sendToYandex());
                        } catch (Exception e) {
                            sub.onError(e);
                        } finally {
                            sub.onCompleted();
                        }
                    }

                    private String sendToYandex() throws IOException {
                        byte audioData[] = output.toByteArray();

                        String yandexKey = System.getenv("YANDEX_SPEECH_KEY");

                        String urlParameters = "?key=" + yandexKey + "&uuid=01ae13cb744628b58fb536d496daa1e6&topic=notes";

                        String url = "https://asr.yandex.net/asr_xml" + urlParameters;

                        System.out.println("\nSending 'POST' request to URL : " + url);
                        System.out.println("Post parameters : " + urlParameters);

                        URL obj = new URL(url);
                        HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

                        //add reuqest header
                        con.setRequestMethod("POST");
                        con.setRequestProperty("Content-Type", "audio/x-pcm;bit=16;rate=16000");
                        con.setRequestProperty("Transfer-Encoding", "chunked");
                        con.setChunkedStreamingMode(10000);

                        // Send post request
                        con.setDoOutput(true);
                        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
                        wr.write(audioData);
                        wr.flush();
                        wr.close();

                        int responseCode = con.getResponseCode();
                        System.out.println("Response Code : " + responseCode);

                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(con.getInputStream()));
                        String inputLine;
                        StringBuffer response = new StringBuffer();

                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        in.close();

                        System.out.println(response.toString());

                        return response.toString();
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
                while (!stopCapture) {
                    int count = targetDataLine.read(data, 0, data.length);
                    if (count > 0) {
                        output.write(data, 0, count);
                    }
                }
                output.close();
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }
}

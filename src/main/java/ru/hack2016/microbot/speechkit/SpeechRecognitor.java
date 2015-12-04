package ru.hack2016.microbot.speechkit;

import rx.Observable;
import rx.Subscriber;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.Arrays;

/**
 * Created by aleksandr on 04.12.15.
 */
public class SpeechRecognitor {
    public static final float SAMPLE_RATE = 16000;
    public static final int SAMPLE_SIZE_IN_BITS = 16;
    public static final int CHANNELS = 1;
    public static final boolean SIGNED = true;
    public static final boolean BIG_ENDIAN = false;

    private static final AudioFormat format = new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_IN_BITS,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN
    );

    TargetDataLine targetDataLine = null;

    public SpeechRecognitor() {
        initTargetLine();
    }

    private void initTargetLine() {
        try {
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);

            targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
            targetDataLine.open(format);
            targetDataLine.start();
        } catch (Exception e) {
            throw new RuntimeException("cannot initialize recognitor", e);
        }
    }

    public Observable<String> recognize() {
        final Observable<String> recognizePoller = Observable.create(
                new Observable.OnSubscribe<String>() {
                    byte[] data = new byte[150000];

                    @Override
                    public void call(Subscriber<? super String> sub) {
                        YandexPoller poller = new YandexPoller();

                        try {
                            while (!Thread.currentThread().isInterrupted()) {
                                int count = targetDataLine.read(data, 0, data.length);

                                PollerThread pollerThread = new PollerThread();
                                pollerThread.setSub(sub);
                                pollerThread.setPoller(poller);
                                pollerThread.setAudioData(Arrays.copyOf(data, count));
                                pollerThread.start();
                            }

                        } catch (Exception e) {
                            sub.onError(e);
                        } finally {
                            sub.onCompleted();
                        }
                    }
                }
        );

        return recognizePoller;
    }

    private class PollerThread extends Thread {
        Subscriber<? super String> sub;
        YandexPoller poller;
        byte[] audioData;

        public void setSub(Subscriber<? super String> sub) {
            this.sub = sub;
        }

        public void setPoller(YandexPoller poller) {
            this.poller = poller;
        }

        public void setAudioData(byte[] audioData) {
            this.audioData = audioData;
        }

        public void run() {
            try {
                sub.onNext(poller.process(audioData));
            } catch (Exception e) {
                System.out.println(e);
                e.printStackTrace();
            }
        }
    }
}

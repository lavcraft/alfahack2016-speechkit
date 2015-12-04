package ru.hack2016.microbot.speechkit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import rx.Subscriber;

/**
 * Created by aleksandr on 04.12.15.
 */
@RunWith(JUnit4.class)
public class SpeechRecognitorTests {

    @Test
    public void simple_test() throws Exception {
        SpeechRecognitor recognitor = new SpeechRecognitor("freeform");

        Subscriber<String> subscriber = new Subscriber<String>() {
            @Override
            public void onNext(String s) { System.out.println(s); }

            @Override
            public void onCompleted() { }

            @Override
            public void onError(Throwable e) { throw new RuntimeException(e); }
        };

        recognitor.recognize().subscribe(subscriber);
    }

}

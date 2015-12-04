package ru.hack2016.microbot.speechkit

import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
import org.apache.http.HttpStatus
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.BasicHttpEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients

import javax.sound.sampled.AudioFormat
import java.nio.charset.StandardCharsets

/**
 * Created by aleksandr on 04.12.15.
 */
public class YandexPoller {
    public static final String YANDEX_SPEECH_CLOUD_ENDPOINT = "https://asr.yandex.net/asr_xml"
    public static final String PCM_TYPE = "audio/x-pcm;bit=16;rate=16000"
    public static final String NO_PARSE_MESSAGE = "no parse"

    private AudioFormat format;

    public YandexPoller(AudioFormat format) {
        this.format = format;
    }

    public String process(String topic, byte[] audioData) throws Exception {
        HttpPost httpPost = new HttpPost("$YANDEX_SPEECH_CLOUD_ENDPOINT${fillUrlParameters(topic)}")

        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, PCM_TYPE)

        httpPost.setEntity(fillEntity(audioData))

        CloseableHttpClient httpClient = HttpClients.custom().build()

        def httpResponse = httpClient.execute(httpPost)

        if (HttpStatus.SC_OK.equals(httpResponse.getStatusLine().getStatusCode())) {
            StringBuilder result = new StringBuilder()

            def parsedResult = new XmlSlurper().parseText(processResponse(httpResponse))

            parsedResult.variant.each { variant ->
                result.append(variant.text()).append(" ")
            }

            if (result.length() == 0) {
                return NO_PARSE_MESSAGE;
            }

            return result.toString()
        } else {
            return "Error: ${httpResponse.getStatusLine().getReasonPhrase()}"
        }
    }

    private String fillUrlParameters(String topic) {
        String yandexKey = System.getenv("YANDEX_SPEECH_KEY")

        StringBuilder urlParameters = new StringBuilder()
        urlParameters.append("?key=")
        urlParameters.append(yandexKey)
        urlParameters.append("&uuid=01ae13cb744628b58fb536d496daa1e6&topic=")
        urlParameters.append(topic)
        urlParameters.toString()
    }

    private HttpEntity fillEntity(byte[] audioData) {
        def entity = new BasicHttpEntity()
        entity.setContent(new ByteArrayInputStream(audioData))
        entity.setChunked(true)
        entity
    }

    private String processResponse(CloseableHttpResponse httpResponse) {
        BufferedReader inputReader = new BufferedReader(
                new InputStreamReader(
                        httpResponse.getEntity().getContent(),
                        StandardCharsets.UTF_8)
        )

        String inputLine;
        StringBuffer response = new StringBuffer()

        while ((inputLine = inputReader.readLine()) != null) {
            response.append(inputLine)
        }
        inputReader.close()
        response.toString()
    }
}

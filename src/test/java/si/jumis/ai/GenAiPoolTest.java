package si.jumis.ai;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class GenAiPoolTest {

    @Test
    void basicUseCase() {
        GenAiPool genAiPool = new GenAiPool("key1", "key", TimeUnit.SECONDS.toNanos(1));
        GenAiClient client = genAiPool.getGenAiClient();
        // use client
        // client1.getModels().generateContent(...)
        // mark last usage
        client.usedNow();
        long last = System.nanoTime();

        // we should get next client immediately
        client = genAiPool.getGenAiClient();
        long now = System.nanoTime();
        System.out.println("Waited " + (now - last) + "ns");
        // use client
        // client2.getModels().generateContent(...)
        // mark last usage
        client.usedNow();
        last = System.nanoTime();

        // this time we have to wait for 60 seconds less the duration of API calls...
        client = genAiPool.getGenAiClient();
        now = System.nanoTime();
        System.out.println("Waited " + (now - last) + "ns");

        client.usedNow();
        last = System.nanoTime();
        // this time we have to wait for 60 seconds less the duration of API calls...
        client = genAiPool.getGenAiClient();
        now = System.nanoTime();
        System.out.println("Waited " + (now - last) + "ns");
        // ...
        assertNotNull(client);
    }
}
package at.aau.serg.controller;

import at.aau.serg.websocketdemoserver.controller.BrightnessController;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BrightnessControllerTest {

    @Test
    public void testSaveBrightness() {

        BrightnessController brightnessController = new BrightnessController();


        float newBrightness = 0.8f;


        String result = brightnessController.saveBrightness(newBrightness);


        assertEquals("Helligkeit gespeichert: " + newBrightness, result);
    }

    @Test
    public void testGetBrightness() {

        BrightnessController brightnessController = new BrightnessController();

        ResponseEntity<Float> responseEntity = brightnessController.getBrightness();

        float brightness = responseEntity.getBody();

        assertEquals(0.5f, brightness);
    }
}

package at.aau.serg.websocketdemoserver.controller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BrightnessController {

    private float currentBrightness = 0.5f;  // Standardwert für Helligkeit


    @PostMapping("/api/brightness/save")
    public String saveBrightness(@RequestParam float brightness) {
        this.currentBrightness = brightness;
        return "Helligkeit gespeichert: " + brightness;
    }


    @GetMapping("/api/brightness/get")
    public ResponseEntity<Float> getBrightness() {
        return ResponseEntity.ok(currentBrightness);
    }
}



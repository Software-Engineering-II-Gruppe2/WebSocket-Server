package at.aau.serg.websocketdemoserver.controller;
import org.springframework.web.bind.annotation.*;
import java.util.List;


    @RestController
    @RequestMapping("/api/sounds")
    public class SoundController {

        private final List<String> availableSounds = List.of("Classic", "Chime", "Beep", "Nature");
        private String selectedSound = "Classic";

        @GetMapping
        public List<String> getAvailableSounds() {
            return availableSounds;
        }

        @PostMapping("/select")
        public String setUserSound(@RequestBody String sound) {
            if (availableSounds.contains(sound)) {
                selectedSound = sound;
                return "Sound gespeichert: " + sound;
            }
            return "Fehler: Sound nicht verfügbar.";
        }
    }


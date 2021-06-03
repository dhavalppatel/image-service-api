package com.InstagramClone.ImageService;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {
	
    @GetMapping("/imageupload")
    public String uploadForm() {
        return "UploadForm";
    }
    
    @GetMapping("/")
    public String homepage() {
        return "Login";
    }

}

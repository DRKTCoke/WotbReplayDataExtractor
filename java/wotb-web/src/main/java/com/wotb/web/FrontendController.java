package com.wotb.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {

    @GetMapping("/extended")
    public String extended() {
        return "forward:/extended.html";
    }
}

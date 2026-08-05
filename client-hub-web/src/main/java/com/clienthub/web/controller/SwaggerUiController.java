package com.clienthub.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SwaggerUiController {

    @GetMapping("/swagger-ui/index.html")
    public String swaggerUi() {
        return "forward:/swagger-auth/index.html";
    }
}

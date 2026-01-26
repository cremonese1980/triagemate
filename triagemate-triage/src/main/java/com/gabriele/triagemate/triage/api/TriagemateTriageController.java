package com.gabriele.triagemate.triage.api;

import com.gabriele.triagemate.triage.api.dto.TriagemateTriageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TriagemateTriageController {

    private static final Logger log = LoggerFactory.getLogger(TriagemateTriageController.class);

    public static final String DEFAULT_TIMEOUT = "1500";

    @GetMapping("/info")
    public TriagemateTriageResponse info() {
        return TriagemateTriageResponse.ok();
    }

    @GetMapping("/info/slow")
    public TriagemateTriageResponse slow(@RequestParam(defaultValue = DEFAULT_TIMEOUT) long ms) throws InterruptedException {
        Thread.sleep(ms);
        log.info("Triage service");
        return TriagemateTriageResponse.ok(ms);


    }


}

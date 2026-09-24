package com.aerosense.aerosense.demo;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo-data")
public class DemoDataController {

  private final DemoDataService demoDataService;

  public DemoDataController(DemoDataService demoDataService) {
    this.demoDataService = demoDataService;
  }

  @PostMapping("/seed")
  public DemoSeedResponse seed() {
    return demoDataService.seed();
  }
}

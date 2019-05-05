package com.ctrip.framework.apollo.portal.controller;

import com.ctrip.framework.apollo.core.enums.Env;
import com.ctrip.framework.apollo.portal.component.PortalSettings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/envs")
public class EnvController {

  private final PortalSettings portalSettings;

  public EnvController(final PortalSettings portalSettings) {
    this.portalSettings = portalSettings;
  }

  @GetMapping
  public List<Env> envs() {
    return portalSettings.getActiveEnvs();
  }

}

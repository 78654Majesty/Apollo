package com.ctrip.framework.apollo.portal.controller;

import com.ctrip.framework.apollo.common.dto.NamespaceDTO;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.exception.ServiceException;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.core.enums.Env;
import com.ctrip.framework.apollo.portal.entity.bo.NamespaceBO;
import com.ctrip.framework.apollo.portal.entity.model.NamespaceTextModel;
import com.ctrip.framework.apollo.portal.service.ItemService;
import com.ctrip.framework.apollo.portal.service.NamespaceService;
import com.ctrip.framework.apollo.portal.util.ConfigToFileUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * jian.tan
 */
@RestController
public class ConfigsExportController {

  private final ItemService configService;

  private final NamespaceService namespaceService;

  public ConfigsExportController(
      final ItemService configService,
      final @Lazy NamespaceService namespaceService) {
    this.configService = configService;
    this.namespaceService = namespaceService;
  }

  @PostMapping("/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/items/import")
  public void importConfigFile(@PathVariable String appId, @PathVariable String env,
      @PathVariable String clusterName, @PathVariable String namespaceName,
      @RequestParam("file") MultipartFile file) {
    if (file.isEmpty()) {
      throw new BadRequestException("The file is empty.");
    }

    NamespaceDTO namespaceDTO = namespaceService
        .loadNamespaceBaseInfo(appId, Env.fromString(env), clusterName, namespaceName);

    if (Objects.isNull(namespaceDTO)) {
      throw new BadRequestException(String.format("Namespace: {} not exist.", namespaceName));
    }

    NamespaceTextModel model = new NamespaceTextModel();
    List<String> fileNameSplit = Splitter.on(".").splitToList(file.getOriginalFilename());
    if (fileNameSplit.size() <= 1) {
      throw new BadRequestException("The file format is invalid.");
    }

    String format = fileNameSplit.get(fileNameSplit.size() - 1);
    model.setFormat(format);
    model.setAppId(appId);
    model.setEnv(env);
    model.setClusterName(clusterName);
    model.setNamespaceName(namespaceName);
    model.setNamespaceId(namespaceDTO.getId());
    String configText;
    try(InputStream in = file.getInputStream()){
      configText = ConfigToFileUtils.fileToString(in);
    }catch (IOException e) {
      throw new ServiceException("Read config file errors:{}", e);
    }
    model.setConfigText(configText);

    configService.updateConfigItemByText(model);
  }

  @GetMapping("/apps/{appId}/envs/{env}/clusters/{clusterName}/namespaces/{namespaceName}/items/export")
  public void exportItems(@PathVariable String appId, @PathVariable String env,
      @PathVariable String clusterName, @PathVariable String namespaceName,
      HttpServletResponse res) {
    List<String> fileNameSplit = Splitter.on(".").splitToList(namespaceName);

    String fileName = fileNameSplit.size() <= 1 ? Joiner.on(".")
        .join(namespaceName, ConfigFileFormat.Properties.getValue()) : namespaceName;
    NamespaceBO namespaceBO = namespaceService.loadNamespaceBO(appId, Env.fromString
        (env), clusterName, namespaceName);

    //generate a file.
    res.setHeader("Content-Disposition", "attachment;filename=" + fileName);

    List<String> fileItems = namespaceBO.getItems().stream().map(itemBO -> {
      String key = itemBO.getItem().getKey();
      String value = itemBO.getItem().getValue();
      if (ConfigConsts.CONFIG_FILE_CONTENT_KEY.equals(key)) {
        return value;
      }

      if ("".equals(key)) {
        return Joiner.on("").join(itemBO.getItem().getKey(), itemBO.getItem().getValue());
      }

      return Joiner.on(" = ").join(itemBO.getItem().getKey(), itemBO.getItem().getValue());
    }).collect(Collectors.toList());

    try {
      ConfigToFileUtils.itemsToFile(res.getOutputStream(), fileItems);
    } catch (Exception e) {
      throw new ServiceException("export items failed:{}", e);
    }
  }
}

package com.reqvars.handler;

import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.logging.Logging;
import com.reqvars.service.PlaceholderService;
import com.reqvars.storage.ConfigManager;

import com.reqvars.service.PlaceholderService.SubstitutionResult;

import java.util.List;

public class ReqVarsHttpHandler implements HttpHandler {

    private final ConfigManager configManager;
    private final PlaceholderService placeholderService;
    private final Logging logging;

    public ReqVarsHttpHandler(ConfigManager configManager, PlaceholderService placeholderService, Logging logging) {
        this.configManager = configManager;
        this.placeholderService = placeholderService;
        this.logging = logging;
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        if (!configManager.isSubstitutionEnabled()) {
            return RequestToBeSentAction.continueWith(requestToBeSent);
        }

        if (!isToolEnabled(requestToBeSent.toolSource())) {
            return RequestToBeSentAction.continueWith(requestToBeSent);
        }

        String rawRequest = requestToBeSent.toString();

        SubstitutionResult result = placeholderService.substituteDetailed(rawRequest, configManager.getVariables());

        if (!result.getUnresolved().isEmpty()) {
            logging.logToOutput("[ReqVars] WARNING: Unresolved placeholders: " + result.getUnresolved());
        }

        String substituted = result.getText();
        if (substituted.equals(rawRequest)) {
            return RequestToBeSentAction.continueWith(requestToBeSent);
        }

        HttpRequest modifiedRequest = HttpRequest.httpRequest(requestToBeSent.httpService(), substituted);
        return RequestToBeSentAction.continueWith(modifiedRequest);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        return ResponseReceivedAction.continueWith(responseReceived);
    }

    private boolean isToolEnabled(burp.api.montoya.core.ToolSource toolSource) {
        for (String tool : configManager.getEnabledTools()) {
            ToolType type = parseToolType(tool);
            if (type != null && toolSource.isFromTool(type)) {
                return true;
            }
        }
        return false;
    }

    private ToolType parseToolType(String name) {
        return switch (name.toUpperCase()) {
            case "REPEATER" -> ToolType.REPEATER;
            case "INTRUDER" -> ToolType.INTRUDER;
            case "SCANNER" -> ToolType.SCANNER;
            case "PROXY" -> ToolType.PROXY;
            case "EXTENSIONS" -> ToolType.EXTENSIONS;
            default -> null;
        };
    }
}

package com.reqvars.handler;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import com.reqvars.model.Variable;
import com.reqvars.service.PlaceholderService;
import com.reqvars.storage.ConfigManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ReqVarsContextMenu implements ContextMenuItemsProvider {

    private final MontoyaApi api;
    private final ConfigManager configManager;
    private final PlaceholderService placeholderService;
    private final Runnable onVariableAdded;

    public ReqVarsContextMenu(MontoyaApi api, ConfigManager configManager,
                              PlaceholderService placeholderService, Runnable onVariableAdded) {
        this.api = api;
        this.configManager = configManager;
        this.placeholderService = placeholderService;
        this.onVariableAdded = onVariableAdded;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<Component> items = new ArrayList<>();

        // "Create variable from selection"
        JMenuItem createVar = new JMenuItem("ReqVars: Create variable from selection");
        createVar.addActionListener(e -> {
            String selection = getSelectedText(event);
            if (selection == null || selection.isEmpty()) {
                JOptionPane.showMessageDialog(null, "No text selected.");
                return;
            }
            String name = JOptionPane.showInputDialog(null, "Variable name:", "Create ReqVars Variable", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                name = name.trim();
                if (!name.matches("[a-zA-Z_][a-zA-Z0-9_-]*")) {
                    JOptionPane.showMessageDialog(null, "Invalid name. Use letters, digits, underscores.");
                    return;
                }
                Variable var = new Variable(name, selection, "", true);
                Long jwtExp = Variable.extractJwtExpiry(selection);
                if (jwtExp != null) {
                    var.setExpiresAt(jwtExp);
                }
                configManager.addVariable(var);
                SwingUtilities.invokeLater(onVariableAdded);
            }
        });
        items.add(createVar);

        // "Anonymize request (reverse substitute)"
        JMenuItem anonymize = new JMenuItem("ReqVars: Replace values with placeholders");
        anonymize.addActionListener(e -> {
            HttpRequestResponse reqRes = event.messageEditorRequestResponse().isPresent()
                    ? event.messageEditorRequestResponse().get().requestResponse()
                    : null;

            if (reqRes == null && !event.selectedRequestResponses().isEmpty()) {
                reqRes = event.selectedRequestResponses().get(0);
            }

            if (reqRes != null && reqRes.request() != null) {
                String raw = reqRes.request().toString();
                String anonymized = placeholderService.reverseSubstitute(raw, configManager.getVariables());

                // Copy to clipboard
                java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(anonymized);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);

                api.logging().logToOutput("[ReqVars] Anonymized request copied to clipboard.");
            }
        });
        items.add(anonymize);

        return items;
    }

    private String getSelectedText(ContextMenuEvent event) {
        if (event.messageEditorRequestResponse().isPresent()) {
            var editor = event.messageEditorRequestResponse().get();
            var selRange = editor.selectionOffsets();
            if (selRange.isPresent()) {
                HttpRequest request = editor.requestResponse().request();
                if (request != null) {
                    String full = request.toString();
                    int start = selRange.get().startIndexInclusive();
                    int end = selRange.get().endIndexExclusive();
                    if (start >= 0 && end <= full.length() && start < end) {
                        return full.substring(start, end);
                    }
                }
            }
        }
        return null;
    }
}

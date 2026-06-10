package com.reqvars;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.reqvars.handler.ReqVarsContextMenu;
import com.reqvars.handler.ReqVarsHttpHandler;
import com.reqvars.service.PlaceholderService;
import com.reqvars.storage.ConfigManager;
import com.reqvars.ui.ReqVarsTab;

public class ReqVarsExtension implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("ReqVars");

        ConfigManager configManager = new ConfigManager(new BurpPersistenceProvider(api));
        PlaceholderService placeholderService = new PlaceholderService();

        ReqVarsTab tab = new ReqVarsTab(configManager, placeholderService);

        ReqVarsHttpHandler handler = new ReqVarsHttpHandler(configManager, placeholderService, api.logging());
        api.http().registerHttpHandler(handler);

        ReqVarsContextMenu contextMenu = new ReqVarsContextMenu(api, configManager, placeholderService, tab::refresh);
        api.userInterface().registerContextMenuItemsProvider(contextMenu);

        api.userInterface().registerSuiteTab("ReqVars", tab);

        api.extension().registerUnloadingHandler(configManager::shutdown);

        api.logging().logToOutput("[ReqVars] Extension loaded. Profile: " + configManager.getActiveProfileName()
                + " | Variables: " + configManager.getVariables().size()
                + " | Tools: " + configManager.getEnabledTools());
    }

    private static class BurpPersistenceProvider implements ConfigManager.PersistenceProvider {

        private final MontoyaApi api;

        BurpPersistenceProvider(MontoyaApi api) {
            this.api = api;
        }

        @Override
        public String load(String key) {
            return api.persistence().preferences().getString(key);
        }

        @Override
        public void save(String key, String value) {
            api.persistence().preferences().setString(key, value);
        }
    }
}

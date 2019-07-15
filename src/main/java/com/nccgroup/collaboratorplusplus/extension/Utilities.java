package com.nccgroup.collaboratorplusplus.extension;

import burp.IBurpExtenderCallbacks;
import com.coreyd97.BurpExtenderUtilities.Preferences;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.HttpHost;

import java.net.Inet4Address;

import static com.nccgroup.collaboratorplusplus.extension.CollaboratorPlusPlus.logManager;
import static com.nccgroup.collaboratorplusplus.extension.CollaboratorPlusPlus.callbacks;
import static com.nccgroup.collaboratorplusplus.extension.Globals.*;

public class Utilities {

    public static HttpHost getBurpProxyHost(String scheme) {
        String configString = callbacks.saveConfigAsJson("proxy.request_listeners");
        JsonObject config = new JsonParser().parse(configString).getAsJsonObject();
        JsonArray listeners = config.getAsJsonObject("proxy").getAsJsonArray("request_listeners");
        for (JsonElement listener : listeners) {
            JsonObject listnerObject = (JsonObject) listener;
            if(listnerObject.get("running").getAsBoolean()){
                int port = listnerObject.get("listener_port").getAsInt();
                String listenMode = listnerObject.get("listen_mode").getAsString();
                if(listenMode.equals("loopback_only")){
                    return new HttpHost("127.0.0.1", port, scheme);
                }
                if(listenMode.equals("all_interfaces")){
                    return new HttpHost("0.0.0.0", port, scheme);
                }
                if(listenMode.equals("specific_address")){
                    String address = listnerObject.get("listen_specific_address").getAsString();
                    return new HttpHost(address, port, scheme);
                }
            }
        }
        return null;
    }

    public static void blockPublicCollaborator(){
        String stringConfig = callbacks.saveConfigAsJson(HOSTNAME_RESOLUTION_CONFIG_PATH);
        JsonObject config = new JsonParser().parse(stringConfig).getAsJsonObject();
        JsonArray resolutionElements = config.getAsJsonObject("project_options")
                                             .getAsJsonObject("connections")
                                             .getAsJsonArray("hostname_resolution");

        boolean shouldAddEntry = true;
        if(resolutionElements.size() > 0){
            for (JsonElement resolutionElement : resolutionElements) {
                String hostname = resolutionElement.getAsJsonObject().get("hostname").getAsString();
                String ip = resolutionElement.getAsJsonObject().get("ip_address").getAsString();
                Boolean enabled = resolutionElement.getAsJsonObject().get("enabled").getAsBoolean();

                if(hostname.equalsIgnoreCase(PUBLIC_COLLABORATOR_HOSTNAME)){
                    if(ip.equalsIgnoreCase("127.0.0.1")){
                        //Existing entry, just make sure its enabled.
                        if(enabled){
                            logManager.logInfo("Sink for public collaborator server already exists, continuing...");
                        }else {
                            logManager.logInfo("Enabling sink for public collaborator server.");
                            resolutionElement.getAsJsonObject().addProperty("enabled", true);
                        }
                        shouldAddEntry = false;
                    }else{
                        //Not our entry,
                        logManager.logInfo("Hostname resolution entry exists for public collaborator server. Disabling and adding sink entry.");
                        resolutionElement.getAsJsonObject().addProperty("enabled", false);
                    }
                    break;
                }
            }
        }else{
            logManager.logInfo("Adding DNS sink for the public collaborator server: \"burpcollaborator.net\" .");
        }
        if(shouldAddEntry){
            resolutionElements.add(buildPublicCollaboratorSink());
            callbacks.loadConfigFromJson(config.toString());
        }
    }

    public static void unblockPublicCollaborator(){
        String stringConfig = callbacks.saveConfigAsJson(HOSTNAME_RESOLUTION_CONFIG_PATH);
        JsonObject config = new JsonParser().parse(stringConfig).getAsJsonObject();
        JsonArray resolutionElements = config.getAsJsonObject("project_options")
                                             .getAsJsonObject("connections")
                                             .getAsJsonArray("hostname_resolution");

        for (JsonElement resolutionElement : resolutionElements) {
            String hostname = resolutionElement.getAsJsonObject().get("hostname").getAsString();
            String ip = resolutionElement.getAsJsonObject().get("ip_address").getAsString();
            Boolean enabled = resolutionElement.getAsJsonObject().get("enabled").getAsBoolean();
            if(hostname.equalsIgnoreCase(PUBLIC_COLLABORATOR_HOSTNAME) && ip.equalsIgnoreCase("127.0.0.1")){
                resolutionElement.getAsJsonObject().addProperty("enabled", false);
                logManager.logInfo("Disabled sink for public collaborator server.");
                break;
            }
        }

        callbacks.loadConfigFromJson(config.toString());
    }

    private static JsonObject buildPublicCollaboratorSink(){
        JsonObject entry = new JsonObject();
        entry.addProperty("enabled", true);
        entry.addProperty("hostname", PUBLIC_COLLABORATOR_HOSTNAME);
        entry.addProperty("ip_address", "127.0.0.1");
        return entry;
    }

    public static void backupCollaboratorConfig(Preferences preferences){
        String config = callbacks.saveConfigAsJson(COLLABORATOR_SERVER_CONFIG_PATH);
        preferences.setSetting(PREF_ORIGINAL_COLLABORATOR_SETTINGS, config);
    }

    public static void restoreCollaboratorConfig(Preferences preferences){
        String config = preferences.getSetting(PREF_ORIGINAL_COLLABORATOR_SETTINGS);
        callbacks.loadConfigFromJson(config);
    }

    public static String buildPollingRedirectionConfig(Preferences preferences, int listenPort){
        return "{\"project_options\": {\"misc\": {\"collaborator_server\": " +
                "{\"location\": \"" + preferences.getSetting(PREF_COLLABORATOR_ADDRESS) + "\"," +
                "\"polling_location\": \"" + Inet4Address.getLoopbackAddress().getHostName() + ":" + listenPort + "\"," +
                "\"poll_over_unencrypted_http\": \"true\"," +
                "\"type\": \"private\"" +
                "}}}}";
    }
}
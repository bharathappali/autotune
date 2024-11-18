package com.autotune.settings;

import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.List;
import java.util.stream.Collectors;

public class SettingsUpdater {
    public static void checkIfInstasliceIsAvailable(CentralSettings centralSettings) {
        try (KubernetesClient client = new DefaultKubernetesClient()) {
            List<DaemonSet> daemonSets = client.apps().daemonSets().inAnyNamespace().list().getItems();
            List<DaemonSet> matchingDaemonSets = daemonSets.stream()
                    .filter(ds -> ds.getMetadata().getName().contains("instaslice"))
                    .toList();
            centralSettings.setAllowGPUResourceUpdates(!matchingDaemonSets.isEmpty());
            client.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void initialiseCentralSettings() {
        CentralSettings centralSettings = CentralSettings.getInstance();

        /**
         * Uncomment the below line in MVP to check for instaslice installation
         */
        // checkIfInstasliceIsAvailable(centralSettings);

        /**
         * The setters below should be replaced with appropriate functions
         * which check and update the settings
         *
         * Hardcoding it for POC
         */
        centralSettings.setKoEditPermissionsAvailable(true);
        centralSettings.setAllowGPUResourceUpdates(true);
    }
}

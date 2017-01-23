package org.eclipse.che.plugin.openshift.client.kubernetes;

public final class KubernetesStringUtils {

    /**
     * Max length of a Kubernetes name or label;
     */
    private static final int MAX_CHARS = 63;

    /**
     * Convenience method for getNormalizedString
     * @param input
     * @return
     */
    public static String getNormalizedString(String input) {
        return getNormalizedString(input, 0);
    }

    /**
     * Converts strings to fit requirements of Kubernetes names and labels.
     * Names in Kubernetes are limited to 63 characters.
     * @param input the string to normalize
     * @param start number of leading characters to trim
     */
    public static String getNormalizedString(String input, int start) {
        int end = Math.min(input.length(), MAX_CHARS + start);
        return input.substring(start, end);
    }

    /**
     * Converts image stream name (e.g. eclipse/ubuntu_jdk8 to eclipse_ubuntu_jdk8)
     * @param repository
     * @return
     */
    public static String getImageStreamName(String repository) {
        return getNormalizedString(repository.replaceAll("/", "_"));
    }

    //TODO: add docs here
    /**
     * Gets tag that should be used for imagestream
     * @param oldRepository
     * @param newRepository
     * @return
     */
    public static String getImageStreamTagName(String oldRepository, String newRepository) {
        String tag = getTagNameFromRepoString(newRepository);
        String repo = getImageStreamName(oldRepository);
        return getNormalizedString(String.format("%s:%s", repo, tag));
    }

//    public static String getImageNameFromRepoString(String label) {
//        // TODO: Maybe add method to trim useless parts
//        String name;
//        if (label.contains("/")) {
//            name = label.split("/")[1];
//        } else {
//            name = label;
//        }
//        return getNormalizedString(name);
//    }

    public static String getTagNameFromRepoString(String repo) {
        String name;
        if (repo.contains("/")) {
            name = repo.split("/")[1];
        } else {
            name = repo;
        }
//        name = String.valueOf(name.hashCode());
        name = name.replaceAll("workspace", "")
                   .replaceAll("machine", "")
                   .replaceAll("che_*", "")
                   .replaceAll("_", "");

        name = "che-ws-" + name;
        return getNormalizedString(name);
    }
}

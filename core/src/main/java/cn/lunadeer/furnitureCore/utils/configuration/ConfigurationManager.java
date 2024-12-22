package cn.lunadeer.furnitureCore.utils.configuration;

import cn.lunadeer.furnitureCore.utils.XLogger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Utility class for loading and saving configuration files.
 * <p>
 * This class uses reflection to read and write configuration files. Capable of reading and writing nested configuration parts.
 */
public class ConfigurationManager {

    /**
     * Load the configuration file.
     *
     * @param clazz The configuration file class. The class should extend {@link ConfigurationFile}.
     * @param file  The file to load.
     * @throws Exception If failed to load the file.
     */
    public static void load(Class<? extends ConfigurationFile> clazz, File file) throws Exception {
        if (!file.exists()) {
            save(clazz, file);
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        readConfigurationFile(yaml, clazz, null);
    }

    /**
     * Load the configuration file and update the version field if needed.
     *
     * @param clazz           The configuration file class. The class should extend {@link ConfigurationFile}.
     * @param file            The file to load.
     * @param versionFieldName The name of the version field.
     * @throws Exception If failed to load the file.
     */
    public static void load(Class<? extends ConfigurationFile> clazz, File file, String versionFieldName) throws Exception {
        Field versionField = clazz.getField(versionFieldName);
        int currentVersion = versionField.getInt(null);
        load(clazz, file);
        if (versionField.getInt(null) != currentVersion) {
            clazz.getField(versionFieldName).set(null, currentVersion);
            save(clazz, file);
        }
    }

    /**
     * Save the configuration file.
     *
     * @param clazz The configuration file class. The class should extend {@link ConfigurationFile}.
     * @param file  The file to save.
     * @throws Exception If failed to save the file.
     */
    public static void save(Class<? extends ConfigurationFile> clazz, File file) throws Exception {
        createIfNotExist(file);
        YamlConfiguration yaml = new YamlConfiguration();
        writeConfigurationFile(yaml, clazz, null);
        yaml.save(file);
    }

    private static void writeConfigurationFile(YamlConfiguration yaml, Class<? extends ConfigurationFile> clazz, String prefix) throws Exception {
        for (Field field : clazz.getFields()) {
            field.setAccessible(true);
            String key = camelToKebab(field.getName());
            if (prefix != null && !prefix.isEmpty()) {
                key = prefix + "." + key;
            }
            // if field is extending ConfigurationPart, recursively write the content
            if (ConfigurationPart.class.isAssignableFrom(field.getType())) {
                XLogger.info("%s is a ConfigurationPart.", field.getName());
                writeConfigurationPart(yaml, (ConfigurationPart) field.get(null), key);
            } else {
                XLogger.info("Writing %s to %s.", field.getName(), key);
                yaml.set(key, field.get(null));
            }
            if (field.isAnnotationPresent(Comment.class)) {
                String comment = field.getAnnotation(Comment.class).value();
                if (comment.contains("\n")) {
                    yaml.setComments(key, List.of(comment.split("\n")));
                } else {
                    yaml.setComments(key, List.of(comment));
                }
            }
        }
    }

    private static void writeConfigurationPart(YamlConfiguration yaml, ConfigurationPart obj, String key) throws Exception {
        for (Field field : obj.getClass().getFields()) {
            field.setAccessible(true);
            String newKey = key + "." + camelToKebab(field.getName());
            if (ConfigurationPart.class.isAssignableFrom(field.getType())) {
                writeConfigurationPart(yaml, (ConfigurationPart) field.get(obj), newKey);
            } else {
                yaml.set(newKey, field.get(obj));
            }
            if (field.isAnnotationPresent(Comment.class)) {
                yaml.setComments(newKey, List.of(field.getAnnotation(Comment.class).value()));
            }
        }
    }

    private static void createIfNotExist(File file) throws Exception {
        if (file.exists()) return;
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs())
            throw new Exception("Failed to create %s directory.".formatted(file.getParentFile().getAbsolutePath()));
        if (!file.createNewFile()) throw new Exception("Failed to create %s file.".formatted(file.getAbsolutePath()));
    }

    private static void readConfigurationFile(YamlConfiguration yaml, Class<? extends ConfigurationFile> clazz, String prefix) throws Exception {
        for (Field field : clazz.getFields()) {
            field.setAccessible(true);
            String key = camelToKebab(field.getName());
            if (prefix != null && !prefix.isEmpty()) {
                key = prefix + "." + key;
            }
            if (!yaml.contains(key)) {
                continue;
            }
            if (ConfigurationPart.class.isAssignableFrom(field.getType())) {
                readConfigurationPart(yaml, (ConfigurationPart) field.get(null), key);
            } else {
                field.set(null, yaml.get(key));
            }
        }
    }

    private static void readConfigurationPart(YamlConfiguration yaml, ConfigurationPart obj, String key) throws Exception {
        for (Field field : obj.getClass().getFields()) {
            field.setAccessible(true);
            String newKey = key + "." + camelToKebab(field.getName());
            if (!yaml.contains(newKey)) {
                continue;
            }
            if (ConfigurationPart.class.isAssignableFrom(field.getType())) {
                readConfigurationPart(yaml, (ConfigurationPart) field.get(obj), newKey);
            } else {
                field.set(obj, yaml.get(newKey));
            }
        }
    }

    /**
     * Converts a camelCase string to kebab-case.
     *
     * @param camel The camelCase string.
     * @return The kebab-case string.
     */
    private static String camelToKebab(String camel) {
        return camel.replaceAll("([a-z])([A-Z]+)", "$1-$2").toLowerCase();
    }

}

using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;

namespace MusicPlayer
{
    public class Settings
    {
        private static Settings instance;
        private static object instanceLock = new object();

        public static Settings Instance()
        {
            if (instance == null)
            {
                lock (instanceLock)
                {
                    if (instance == null)
                    {
                        instance = new Settings();
                    }
                }
            }
            return instance;
        }

        public byte LastTabIndex = 0;

        public void SaveSettings()
        {
            SettingsClass settings = new SettingsClass
            {
                lastTabIndex = LastTabIndex,
            };
            string json = JsonSerializer.Serialize(settings);
            File.WriteAllText(Path.Combine(FileSystem.AppDataDirectory, "settings.json"), json);
        }

        public void SaveSettings(byte LastTab)
        {
            SettingsClass settings = new SettingsClass
            {
                lastTabIndex = LastTab,
            };
            string json = JsonSerializer.Serialize(settings);
            File.WriteAllText(Path.Combine(FileSystem.AppDataDirectory, "settings.json"), json);
        }

        public void LoadSettings()
        {
            string path = Path.Combine(FileSystem.AppDataDirectory, "settings.json");
            if (File.Exists(path))
            {
                string json = File.ReadAllText(path);
                SettingsClass settings = JsonSerializer.Deserialize<SettingsClass>(json);
                LastTabIndex = settings.lastTabIndex;
            }
        }

        public class SettingsClass
        {
            public byte lastTabIndex { get; set; }
        }
    }
}

using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;

namespace MusicPlayer
{
    public class Settings : INotifyPropertyChanged
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


        //Values ------------------------------------------------------------------------
        private string _source;
        public string Source
        {
            get => _source;
            set
            {
                if (_source != value)
                {
                    _source = value;
                    OnPropertyChanged(nameof(Source));
                }
            }
        }

        public bool IsFirstOpenTime = true;

        public byte LastTabIndex = 0;
        public string LastSongPath;
        public int LastSongTime = 0;
        public string LastSongName = "";
        public string LastSongArtist = "";
        public string LastSongCoverPath = "";


        //Save or Load settings ------------------------------------------------------------------------
        public void SaveSettings()
        {
            SettingsClass settings = new SettingsClass
            {
                lastTabIndex = LastTabIndex,
                lastSongPath = LastSongPath,
                lastSongTime = LastSongTime,
                lastSongName = LastSongName,
                lastSongArtist = LastSongArtist,
            };
            string json = JsonSerializer.Serialize(settings);
            File.WriteAllText(Path.Combine(FileSystem.AppDataDirectory, "settings.json"), json);
        }

        public void SaveSettings(byte LastTab)
        {
            SettingsClass settings = new SettingsClass
            {
                lastTabIndex = LastTab,
                lastSongPath = LastSongPath,
                lastSongTime = LastSongTime,
                lastSongName = LastSongName,
                lastSongArtist= LastSongArtist,
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
                LastSongPath = settings.lastSongPath;
                LastSongTime = settings.lastSongTime;
                LastSongName = settings.lastSongName;
                LastSongArtist = settings.lastSongArtist;
            }
        }

        public class SettingsClass
        {
            public byte lastTabIndex { get; set; }
            public string lastSongPath { get; set; }
            public int lastSongTime { get; set; }
            public string lastSongName { get; set; }
            public string lastSongArtist { get; set; }
        }

        public event PropertyChangedEventHandler PropertyChanged;
        protected void OnPropertyChanged([CallerMemberName] string propertyName = null) =>
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
    }
}

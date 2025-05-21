using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MusicPlayer
{
    public class Song
    {
        public string Title { get; set; }
        public string Artist { get; set; }
        public string Album { get; set; }
        public string Path { get; set; }
        public double LengthInSec { get; set; }
        public string LengthInString { get; set; }

        public Song(string title, string artist, string album, string path, double length)
        {
            this.Title = title;
            this.Artist = artist;
            this.Album = album;
            this.Path = path;
            this.LengthInSec = length;
            this.LengthInString = getLengthInNormalTime(this);
        }

        private string getLengthInNormalTime(Song song)
        {
            int hours = Convert.ToInt32(song.LengthInSec / 3600);
            byte minutes = Convert.ToByte((song.LengthInSec % 3600) / 60);
            byte seconds = Convert.ToByte(song.LengthInSec % 60);

            if (hours > 0)
            {
                return $"{hours}:{minutes:D2}:{seconds:D2}";
            }
            else
            {
                return $"{minutes:D2}:{seconds:D2}";
            }
        }
    }
}

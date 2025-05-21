using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MusicPlayer
{
    public class Playlist
    {
        public string Title { get; set; }
        public List<Song> Songs { get; set; }
        public string LengthInString { get; set; }

        public Playlist(string title, List<Song> songs)
        {
            this.Title = title;
            this.Songs = songs;
            this.LengthInString = getLengthInNormalTime(songs);
        }

        public void AddSong(Song song)
        {
            this.Songs.Add(song);
            this.LengthInString = getLengthInNormalTime(this.Songs);
        }

        public void setName(string name)
        {
            this.Title = name;
        }

        private string getLengthInNormalTime(List<Song> songs)
        {
            double totalDuration = 0;

            foreach (Song s in songs)
            {
                totalDuration += s.LengthInSec;
            }

            int hours = Convert.ToInt32(totalDuration / 3600);
            byte minutes = Convert.ToByte((totalDuration % 3600) / 60);
            byte seconds = Convert.ToByte(totalDuration % 60);

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

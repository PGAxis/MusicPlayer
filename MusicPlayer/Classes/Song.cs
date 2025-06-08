using SQLite;

namespace MusicPlayer
{
    public class Song
    {
        [PrimaryKey, AutoIncrement]
        public int Id { get; set; }
        public string Title { get; set; }
        public string Artist { get; set; }
        public string Album { get; set; }
        public string AlbumArt { get; set; }
        public string Path { get; set; }
        public double LengthInSec { get; set; }
        public string LengthInString { get; set; }

        public Song()
        {

        }

        public Song(string title, string artist, string album, string album_art, string path, double length)
        {
            this.Title = title;
            this.Artist = artist;
            this.Album = album;
            this.AlbumArt = album_art;
            this.Path = path;
            this.LengthInSec = length;
            this.LengthInString = getLengthInNormalTime(this);
        }

        private string getLengthInNormalTime(Song song)
        {
            int hours = Convert.ToInt32(song.LengthInSec / 3600);
            byte minutes = Convert.ToByte((song.LengthInSec % 3600) / 60);
            byte seconds = Convert.ToByte(song.LengthInSec % 60);

            return (hours > 0 ? $"{hours}:{minutes:D2}:{seconds:D2}" : $"{minutes:D2}:{seconds:D2}");
        }
    }
}

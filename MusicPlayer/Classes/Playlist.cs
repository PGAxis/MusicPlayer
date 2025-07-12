using SQLite;

namespace MusicPlayer
{
    public class Playlist
    {
        [PrimaryKey, AutoIncrement]
        public int Id { get; set; }
        public string Title { get; set; }
        public string PlaylistCover { get; set; } = "default_cover.png";
        //public string LengthInString { get; set; }

        public Playlist()
        {

        }

        public Playlist(string title)
        {
            this.Title = title;
        }

        public void setName(string name)
        {
            this.Title = name;
        }

        private async void SetAlbumCover(int songId)
        {
            Song song = await App.SongDatabase.GetSongByIdAsync(songId);
            this.PlaylistCover = song.AlbumArt;
        }

        /*private string getLengthInNormalTime(List<Song> songs)
        {
            double totalDuration = 0;

            foreach (Song s in songs)
            {
                totalDuration += s.LengthInSec;
            }

            int hours = Convert.ToInt32(totalDuration / 3600);
            byte minutes = Convert.ToByte((totalDuration % 3600) / 60);
            byte seconds = Convert.ToByte(totalDuration % 60);

            return (hours > 0 ? $"{hours}:{minutes:D2}:{seconds:D2}" : $"{minutes:D2}:{seconds:D2}");
        }*/
    }
}

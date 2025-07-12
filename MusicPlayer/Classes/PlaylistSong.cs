using SQLite;

namespace MusicPlayer
{
    public class PlaylistSong
    {
        [PrimaryKey, AutoIncrement]
        public int Id { get; set; }

        public int PlaylistId { get; set; }
        public int SongId { get; set; }
        public int Position { get; set; }
    }
}

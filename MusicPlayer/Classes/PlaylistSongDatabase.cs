using SQLite;

namespace MusicPlayer
{
    public class PlaylistSongDatabase
    {
        readonly SQLiteAsyncConnection _database;

        public PlaylistSongDatabase(string dbPath)
        {
            _database = new SQLiteAsyncConnection(dbPath);
            _database.CreateTableAsync<PlaylistSong>().Wait();
        }

        public async Task<int> AddSongToPlaylist(int playlistId, int  songId)
        {
            PlaylistSong entry = new PlaylistSong
            {
                PlaylistId = playlistId,
                SongId = songId
            };

            return await _database.InsertAsync(entry);
        }

        public async Task<PlaylistWithSongs?> GetFilledPlaylistByIdAsync(int playlistId)
        {
            var playlist = await App.PlaylistDatabase.GetPlaylistByIdAsync(playlistId);

            if (playlist == null)
            {
                return null;
            }

            List<PlaylistSong> tmpSongs = await _database.Table<PlaylistSong>().Where(ps => ps.PlaylistId == playlistId).ToListAsync();
            List<int> songIds = tmpSongs.Select(song => song.SongId).ToList();

            var allSongs = await App.SongDatabase.GetSongsAsync();
            var songs = allSongs.Where(s => songIds.Contains(s.Id)).ToList();

            return new PlaylistWithSongs
            {
                Title = playlist.Title,
                PlaylistCover = playlist.PlaylistCover,
                Songs = songs,
            };
        }
    }
}

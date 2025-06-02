using SQLite;

namespace MusicPlayer
{
    public class SongDatabase
    {
        readonly SQLiteAsyncConnection _database;

        public SongDatabase(string dbPath)
        {
            _database = new SQLiteAsyncConnection(dbPath);
            _database.CreateTableAsync<Song>().Wait();
        }

        public Task<List<Song>> GetSongsAsync()
        {
            return _database.Table<Song>().ToListAsync();
        }

        public Task<int> SaveSongAsync(Song song)
        {
            return _database.InsertAsync(song);
        }

        public Task<int> DeleteSongAsync(Song song)
        {
            return _database.DeleteAsync(song);
        }
    }
}

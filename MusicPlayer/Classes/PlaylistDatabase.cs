using SQLite;

namespace MusicPlayer
{
    public class PlaylistDatabase
    {
        readonly SQLiteAsyncConnection _database;

        public PlaylistDatabase(string dbPath)
        {
            _database = new SQLiteAsyncConnection(dbPath);
            _database.CreateTableAsync<Playlist>().Wait();
        }

        public Task<List<Playlist>> GetPlaylistsAsync()
        {
            return _database.Table<Playlist>().ToListAsync();
        }

        public Task<int> SavePlaylistAsync(Playlist playlist)
        {
            return _database.InsertOrReplaceAsync(playlist);
        }

        public Task<int> DeletePlaylistAsync(Playlist playlist)
        {
            return _database.DeleteAsync(playlist);
        }

        public Task<Playlist> GetPlaylistByIdAsync(int id)
        {
            return _database.Table<Playlist>().Where(p => p.Id == id).FirstOrDefaultAsync();
        }
    }
}

namespace MusicPlayer
{
    public partial class App : Application
    {
        public static SongDatabase SongDatabase { get; private set; }
        public static PlaylistDatabase PlaylistDatabase { get; private set; }
        public static PlaylistSongDatabase PlaylistSongDatabase { get; private set; }

        public App()
        {
            InitializeComponent();

            string dbSPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "songs.db3");
            string dbPPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "playlists.db3");
            string dbPSPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "playlistSong.db3");
            SongDatabase = new SongDatabase(dbSPath);
            PlaylistDatabase = new PlaylistDatabase(dbPPath);
            PlaylistSongDatabase = new PlaylistSongDatabase(dbPSPath);

            Routing.RegisterRoute(nameof(SongSelector), typeof(SongSelector));

            MainPage = new AppShell();
        }
    }
}

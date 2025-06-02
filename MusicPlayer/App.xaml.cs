namespace MusicPlayer
{
    public partial class App : Application
    {
        public static SongDatabase SongDatabase { get; private set; }

        public App()
        {
            InitializeComponent();

            string dbPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "songs.db3");
            SongDatabase = new SongDatabase(dbPath);

            Routing.RegisterRoute(nameof(SongSelector), typeof(SongSelector));

            MainPage = new AppShell();
        }
    }
}

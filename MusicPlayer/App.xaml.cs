namespace MusicPlayer
{
    public partial class App : Application
    {
        public static IServiceProvider ServiceProvider;
        public App(IServiceProvider serviceProvider)
        {
            InitializeComponent();

            MainPage = new AppShell();
            ServiceProvider = serviceProvider;
        }
    }
}

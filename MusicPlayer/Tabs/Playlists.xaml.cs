using System.Collections.ObjectModel;
using System.ComponentModel;

namespace MusicPlayer;

public partial class Playlists : ContentPage, IResizablePage
{
	private static Playlists instance;
	private static object instanceLock = new object();

    public ObservableCollection<PlaylistTemplate> PlaylistList { get; set; }

    public static Playlists Instance()
	{
		if (instance == null)
		{
			lock (instanceLock)
			{
				if (instance == null)
				{
					instance = new Playlists();
				}
			}
		}
		return instance;
	}

	private Playlists()
	{
		InitializeComponent();
        MainStack.WidthRequest = DeviceDisplay.MainDisplayInfo.Width / DeviceDisplay.MainDisplayInfo.Density;
		PlaylistList = new ObservableCollection<PlaylistTemplate>
		{
			new PlaylistTemplate {PlaylistCover = "default_cover.png", Title = "Playlist 1", Songs = new List<Song>{ new Song("Jaj", "ToCo", "album", "album art", "path", 3000) } },
            //new PlaylistTemplate {Image = "default_cover.png", Title = "Playlist 2", SongCount = "1"},
        };
        PlaylistsColView.BindingContext = this;
    }

	public class PlaylistTemplate
	{
        public string Title { get; set; }
        public List<Song> Songs { get; set; }
        public string PlaylistCover { get; set; }
        public string LengthInString { get; set; }
    }

    public void ChangeWidth()
    {
        MainStack.WidthRequest = DeviceDisplay.MainDisplayInfo.Width / DeviceDisplay.MainDisplayInfo.Density;
    }
}
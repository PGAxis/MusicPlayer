using System.Collections.ObjectModel;
using System.ComponentModel;

namespace MusicPlayer;

public partial class Playlists : ContentPage
{
	private static Playlists instance;
	private static object instanceLock = new object();

    public ObservableCollection<PlaylistTemplate> Templates { get; set; }

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
		Templates = new ObservableCollection<PlaylistTemplate>
		{
			new PlaylistTemplate {Image = "default_playlist.png", Title = "Playlist 1", SongCount = "1"},
            new PlaylistTemplate {Image = "default_playlist.png", Title = "Playlist 2", SongCount = "1"},
        };
        PlaylistsColView.BindingContext = this;
    }

	public class PlaylistTemplate : INotifyPropertyChanged
	{
		public string Image {  get; set; }
		public string Title { get; set; }

		private string songCount;
		public string SongCount
		{
			get => songCount;
			set
			{
				if (songCount != value)
				{
					songCount = value;
					OnPropertyChanged(nameof(SongCount));
				}
			}
		}

        public event PropertyChangedEventHandler PropertyChanged;
        protected void OnPropertyChanged(string name) => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
    }
}
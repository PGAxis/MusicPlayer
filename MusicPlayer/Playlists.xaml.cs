using static System.Net.Mime.MediaTypeNames;

namespace MusicPlayer;

public partial class Playlists : ContentPage
{
	public Playlists()
	{
		InitializeComponent();
        PlaylistsLabel.WidthRequest = (DeviceDisplay.MainDisplayInfo.Width) / (DeviceDisplay.MainDisplayInfo.Density);
        PlaylistsLabel.Text = DeviceDisplay.MainDisplayInfo.Width.ToString();
    }
}
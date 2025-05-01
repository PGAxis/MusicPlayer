using CommunityToolkit.Maui.Core;
using System.Collections.ObjectModel;
using System.Runtime.CompilerServices;
using System.Windows.Input;

namespace MusicPlayer
{
    public partial class MainPage : ContentPage
    {
        public MainPage()
        {
            InitializeComponent();
            SetCorrectWidthRequest();
            FavouritesView.Content = new Favourites().Content;
            PlaylistsView.Content = new Playlists().Content;
        }

        private void UserScrolled(object sender, ScrolledEventArgs e)
        {
            double curScrollX = e.ScrollX;
            double ViewScrollX = curScrollX * 2;
            _ = ViewScroll.ScrollToAsync(ViewScrollX, 0, false);

            _ = CheckScrollChange(curScrollX);
        }



        //Help methods
        private void SetCorrectWidthRequest()
        {
            double ViewRequest = (DeviceDisplay.MainDisplayInfo.Width) / (DeviceDisplay.MainDisplayInfo.Density);
            double LabelRequest = ViewRequest * 0.5;
            double PaddingRequest = (ViewRequest - LabelRequest) / 2;

            LeftPadding.WidthRequest = PaddingRequest;
            RightPadding.WidthRequest = PaddingRequest;

            FavLabel.WidthRequest = LabelRequest;
            PlayLabel.WidthRequest = LabelRequest;
            SongLabel.WidthRequest = LabelRequest;
            AlbLabel.WidthRequest = LabelRequest;
            IntLabel.WidthRequest = LabelRequest;

            FavouritesView.WidthRequest = ViewRequest;
            PlaylistsView.WidthRequest = ViewRequest;
            SongsView.WidthRequest = ViewRequest;
            AlbumsView.WidthRequest = ViewRequest;
            InterpretsView.WidthRequest = ViewRequest;
        }

        private async Task CheckScrollChange(double scrollX)
        {
            await Task.Delay(10);

            double curX = TabScroll.ScrollX;

            bool hasntChanged = AreCloseEnough(curX, scrollX);

            if (hasntChanged)
            {

            }
        }

        private bool AreCloseEnough(double a, double b)
        {
            return Math.Abs(a - b) == 0.0;
        }
    }
}

using Microsoft.Maui;

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

            _ = CheckScrollChange(curScrollX, TabScroll);
        }

        private void ViewScrolled(object sender, ScrolledEventArgs e)
        {
            double curScrollX = e.ScrollX;
            double TabScrollX = curScrollX * 0.5;
            _ = TabScroll.ScrollToAsync(TabScrollX, 0, false);

            _ = CheckScrollChange(curScrollX, ViewScroll);
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

        private async Task CheckScrollChange(double scrollX, ScrollView sender)
        {
            await Task.Delay(100);

            double curX = sender.ScrollX;

            bool hasntChanged = AreCloseEnough(curX, scrollX);

            if (hasntChanged)
            {
                await ViewScroll.ScrollToAsync(GetClosestView(), ScrollToPosition.Center, false);
            }
        }

        private bool AreCloseEnough(double a, double b)
        {
            return Math.Abs(a - b) == 0.0;
        }

        private ContentView GetClosestView()
        {
            double scrollX = ViewScroll.ScrollX;
            double scrollWidth = ViewScroll.Width;
            double viewportCenter = scrollX + scrollWidth / 2;

            var stack = (HorizontalStackLayout)ViewScroll.Content;

            ContentView closest = null;
            double smallestDistance = double.MaxValue;

            double runningX = 0;

            foreach (var child in stack.Children)
            {
                double childCenter = runningX + child.Width / 2;

                double distance = Math.Abs(viewportCenter - childCenter);
                if (distance < smallestDistance)
                {
                    smallestDistance = distance;
                    closest = (ContentView)child;
                }

                runningX += child.Width;
            }

            return closest;
        }
    }
}

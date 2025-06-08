using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MusicPlayer
{
    public class AlbumArtConverter : IValueConverter
    {
        public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
        {
            var path = value as string;
            //Console.WriteLine(path);
            if (!string.IsNullOrWhiteSpace(path) && File.Exists(path))
                return ImageSource.FromFile(path);

            return "default_cover.png";
        }

        public object ConvertBack(object value, Type targetType, object parameter, System.Globalization.CultureInfo culture)
                => throw new NotImplementedException();
    }
}

import { createTheme } from '@mui/material';

export default function (mode) {
  return createTheme({
    status: {
      danger: '#e53e3e',
    },
    palette: {
      mode,
      primary: {
        light: '#757ce8',
        main: '#00256c',
        dark: '#002884',
        contrastText: '#fff',
      },

      secondary: {
        light: '#ff7961',
        main: '#ffffff',
        dark: '#ba000d',
        contrastText: '#000',
      },
    },

    typography: {
      subtitle1: {
        fontSize: '0.77rem',
      },
      body1: {
        fontSize: '0.9rem',
      },
    },

    spacing: 4,

    components: {
      MuiAutocomplete: {
        styleOverrides: {
          inputRoot: {
            padding: 4,
          },
        },
      },

      MuiTypography: {
        styleOverrides: {
          root: {
            textTransform: 'none',
          },
        },
      },

      MuiButtonBase: {
        styleOverrides: {
          root: {
            padding: 0,
          },
        },
      },

      MuiButton: {
        styleOverrides: {
          root: {
            textTransform: 'none',
          },
        },
      },

      MuiNativeSelect: {
        styleOverrides: {
          root: {
            padding: 'unset',
          },
        },
      },

      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            padding: '4px',
          },
          input: {
            padding: '8px 8px',
          },
        },
      },
    },
  });
}

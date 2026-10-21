# Fonts

The app sets its text in two typefaces, both under the SIL Open Font License 1.1
(the license texts are beside this file):

- **Rubik** (The Rubik Project Authors): all body text, labels and buttons.
- **Frank Ruhl Libre** (The Frank Ruhl Libre Project Authors): screen titles and headings,
  the voice of the atlas.

The files in `app/src/main/res/font` are static instances of the variable fonts in
[google/fonts](https://github.com/google/fonts), cut to the weights the app uses and
subset to Hebrew, Latin and common punctuation:

```sh
fonttools varLib.instancer Rubik[wght].ttf wght=500 -o rubik_medium.ttf
pyftsubset rubik_medium.ttf --layout-features='*' \
  --unicodes="U+0000-00FF,U+0590-05FF,U+2000-206F,U+20AA,U+2190-21FF,U+2212,U+FB1D-FB4F"
```

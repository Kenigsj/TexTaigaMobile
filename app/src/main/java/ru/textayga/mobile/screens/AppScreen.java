package ru.textayga.mobile.screens;

import android.view.View;

import ru.textayga.mobile.MainActivity;

// база для экранов, чтоб не таскать одно и то же
public interface AppScreen {
    // host - MainActivity, оттуда беру базу, ui и переходы
    View render(MainActivity host);
}

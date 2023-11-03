package app.pachli.lint.checks

import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class AndroidxToolbarDetectorTest {
    @Test
    fun testError() {
        lint().files(
            xml(
                "res/layout/test.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                <androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    tools:context="app.pachli.components.search.SearchActivity">

                    <com.google.android.material.appbar.AppBarLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        app:liftOnScroll="true"
                        app:liftOnScrollTargetViewId="@id/pages"
                        app:layout_collapseMode="pin">

                        <androidx.appcompat.widget.Toolbar
                            android:id="@+id/toolbar"
                            android:layout_width="match_parent"
                            android:layout_height="?attr/actionBarSize"
                            app:contentInsetStartWithNavigation="0dp"
                            app:layout_scrollFlags="scroll|snap|enterAlways"
                            app:navigationIcon="?attr/homeAsUpIndicator" />

                    </com.google.android.material.appbar.AppBarLayout>

                </androidx.coordinatorlayout.widget.CoordinatorLayout>
            """,
            ).indented(),
        ).issues(AndroidxToolbarDetector.ISSUE).allowMissingSdk().run().expectWarningCount(1)
            .expect(
                """res/layout/test.xml:16: Warning: Use <com.google.android.material.appbar.MaterialToolbar> instead of <androidx.appcompat.widget.Toolbar> [AndroidxToolbarDetector]
                        <androidx.appcompat.widget.Toolbar
                        ^
0 errors, 1 warnings
        """,
            )
    }

    @Test
    fun testNoError() {
        lint().files(
            xml(
                "res/layout/test.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                <androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    tools:context="app.pachli.components.search.SearchActivity">

                    <com.google.android.material.appbar.AppBarLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        app:layout_collapseMode="pin"
                        app:liftOnScroll="true"
                        app:liftOnScrollTargetViewId="@id/pages">

                        <com.google.android.material.appbar.MaterialToolbar
                            android:id="@+id/toolbar"
                            android:layout_width="match_parent"
                            android:layout_height="?attr/actionBarSize"
                            app:contentInsetStartWithNavigation="0dp"
                            app:layout_scrollFlags="scroll|snap|enterAlways"
                            app:navigationIcon="?attr/homeAsUpIndicator" />

                    </com.google.android.material.appbar.AppBarLayout>

                </androidx.coordinatorlayout.widget.CoordinatorLayout>
                """,
            ).indented(),
        ).issues(AndroidxToolbarDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectWarningCount(0)
    }
}

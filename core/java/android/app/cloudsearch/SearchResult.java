/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.app.cloudsearch;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.StringDef;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * A {@link SearchResult} includes all the information for one result item.
 *
 * @hide
 */
@SystemApi
public final class SearchResult implements Parcelable {

    /** Short content best describing the result item. */
    @NonNull
    private final String mTitle;

    /** Matched contents in the result item. */
    @NonNull
    private final String mSnippet;

    /** Ranking Score provided by the search provider. */
    private final float mScore;

    /**
     * List of public static KEYS for Bundles in mExtraInfos.
     * mExtraInfos contains various information specified for different data types.
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef(prefix = {"EXTRAINFO_"},
        value = {EXTRAINFO_APP_DOMAIN_URL,
            EXTRAINFO_APP_ICON,
            EXTRAINFO_APP_DEVELOPER_NAME,
            EXTRAINFO_APP_SIZE_BYTES,
            EXTRAINFO_APP_STAR_RATING,
            EXTRAINFO_APP_IARC,
            EXTRAINFO_APP_REVIEW_COUNT,
            EXTRAINFO_APP_CONTAINS_ADS_DISCLAIMER,
            EXTRAINFO_APP_CONTAINS_IAP_DISCLAIMER,
            EXTRAINFO_SHORT_DESCRIPTION,
            EXTRAINFO_LONG_DESCRIPTION,
            EXTRAINFO_SCREENSHOTS,
            EXTRAINFO_APP_BADGES,
            EXTRAINFO_ACTION_BUTTON_TEXT_PREREGISTERING,
            EXTRAINFO_ACTION_BUTTON_IMAGE_PREREGISTERING,
            EXTRAINFO_WEB_URL,
            EXTRAINFO_WEB_ICON})
    public @interface SearchResultExtraInfoKey {}
    /** This App developer website's domain URL, String value expected. */
    public static final String EXTRAINFO_APP_DOMAIN_URL = "APP_DOMAIN_URL";
    /** This App result's ICON URL, String value expected. */
    public static final String EXTRAINFO_APP_ICON = "APP_ICON";
    /** This App developer's name, String value expected. */
    public static final String EXTRAINFO_APP_DEVELOPER_NAME = "APP_DEVELOPER_NAME";
    /** This App's pkg size in bytes, Double value expected. */
    public static final String EXTRAINFO_APP_SIZE_BYTES = "APP_SIZE_BYTES";
    /** This App developer's name, Double value expected. */
    public static final String EXTRAINFO_APP_STAR_RATING = "APP_STAR_RATING";
    /** This App's IARC rating, String value expected.
     * IARC (International Age Rating Coalition) is partnered globally with major
     * content rating organizations to provide a centralized and one-stop-shop for
     * rating content on a global scale.
     */
    public static final String EXTRAINFO_APP_IARC = "APP_IARC";
    /** This App's review count, Double value expected. */
    public static final String EXTRAINFO_APP_REVIEW_COUNT = "APP_REVIEW_COUNT";
    /** If this App contains the Ads Disclaimer, Boolean value expected. */
    public static final String EXTRAINFO_APP_CONTAINS_ADS_DISCLAIMER =
            "APP_CONTAINS_ADS_DISCLAIMER";
    /** If this App contains the IAP Disclaimer, Boolean value expected. */
    public static final String EXTRAINFO_APP_CONTAINS_IAP_DISCLAIMER =
            "APP_CONTAINS_IAP_DISCLAIMER";
    /** This App's short description, String value expected. */
    public static final String EXTRAINFO_SHORT_DESCRIPTION = "SHORT_DESCRIPTION";
    /** This App's long description, String value expected. */
    public static final String EXTRAINFO_LONG_DESCRIPTION = "LONG_DESCRIPTION";
    /** This App's screenshots, List<ImageLoadingBundle> value expected. */
    public static final String EXTRAINFO_SCREENSHOTS = "SCREENSHOTS";
    /** Editor's choices for this App, ArrayList<String> value expected. */
    public static final String EXTRAINFO_APP_BADGES = "APP_BADGES";
    /** Pre-registration game's action button text, String value expected. */
    @SuppressLint("IntentName")
    public static final String EXTRAINFO_ACTION_BUTTON_TEXT_PREREGISTERING = "ACTION_BUTTON_TEXT";
    /** Pre-registration game's action button image, ImageLoadingBundle value expected. */
    @SuppressLint("IntentName")
    public static final String EXTRAINFO_ACTION_BUTTON_IMAGE_PREREGISTERING = "ACTION_BUTTON_IMAGE";
    /** Web content's URL, String value expected. */
    public static final String EXTRAINFO_WEB_URL = "WEB_URL";
    /** Web content's domain icon URL, String value expected. */
    public static final String EXTRAINFO_WEB_ICON = "WEB_ICON";

    @NonNull
    private Bundle mExtraInfos;

    private SearchResult(Parcel in) {
        this.mTitle = in.readString();
        this.mSnippet = in.readString();
        this.mScore = in.readFloat();
        this.mExtraInfos = in.readBundle();
    }

    private SearchResult(String title, String snippet, float score, Bundle extraInfos) {
        mTitle = title;
        mSnippet = snippet;
        mScore = score;
        mExtraInfos = extraInfos;
    }

    /** Gets the search result title. */
    @NonNull
    public String getTitle() {
        return mTitle;
    }

    /** Gets the search result snippet. */
    @NonNull
    public String getSnippet() {
        return mSnippet;
    }

    /** Gets the ranking score provided by the original search provider. */
    public float getScore() {
        return mScore;
    }

    /** Gets the extra information associated with the search result. */
    @NonNull
    public Bundle getExtraInfos() {
        return mExtraInfos;
    }

    private SearchResult(Builder b) {
        mTitle = requireNonNull(b.mTitle);
        mSnippet = requireNonNull(b.mSnippet);
        mScore = b.mScore;
        mExtraInfos = requireNonNull(b.mExtraInfos);
    }

    /**
     *
     * @see Creator
     *
     */
    @NonNull public static final Creator<SearchResult> CREATOR = new Creator<SearchResult>() {
        @Override
        public SearchResult createFromParcel(Parcel p) {
            return new SearchResult(p);
        }

        @Override
        public SearchResult[] newArray(int size) {
            return new SearchResult[size];
        }
    };

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(this.mTitle);
        dest.writeString(this.mSnippet);
        dest.writeFloat(this.mScore);
        dest.writeBundle(this.mExtraInfos);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        SearchResult that = (SearchResult) obj;
        return Objects.equals(mTitle, that.mTitle)
            && Objects.equals(mSnippet, that.mSnippet)
            && mScore == that.mScore
            && Objects.equals(mExtraInfos, that.mExtraInfos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTitle, mSnippet, mScore, mExtraInfos);
    }

    /**
     * Builder constructing SearchResult.
     *
     * @hide
     */
    @SystemApi
    public static final class Builder {
        private String mTitle;
        private String mSnippet;
        private float mScore;
        private Bundle mExtraInfos;

        /**
         *
         * @param title the title to the search result.
         * @param extraInfos the extra infos associated with the search result.
         *
         * @hide
         */
        @SystemApi
        public Builder(@NonNull String title, @NonNull Bundle extraInfos) {
            mTitle = title;
            mExtraInfos = extraInfos;

            mSnippet = "";
            mScore = 0;
        }

        /** Sets the title to the search result. */
        @NonNull
        public Builder setTitle(@NonNull String title) {
            this.mTitle = title;
            return this;
        }

        /** Sets the snippet to the search result. */
        @NonNull
        public Builder setSnippet(@NonNull String snippet) {
            this.mSnippet = snippet;
            return this;
        }

        /** Sets the ranking score to the search result. */
        @NonNull
        public Builder setScore(float score) {
            this.mScore = score;
            return this;
        }

        /** Adds extra information to the search result for rendering in the UI. */
        @NonNull
        public Builder setExtraInfos(@NonNull Bundle extraInfos) {
            this.mExtraInfos = extraInfos;
            return this;
        }

        /** Builds a SearchResult based-on the given parameters. */
        @NonNull
        public SearchResult build() {
            if (mTitle == null || mExtraInfos == null || mSnippet == null) {
                throw new IllegalStateException("Please make sure all required args are assigned.");
            }

            return new SearchResult(mTitle, mSnippet, mScore, mExtraInfos);
        }
    }
}

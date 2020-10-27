/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.codegentest;

import android.os.Parcelable;

import com.android.internal.util.DataClass;

/**
 * @see HierrarchicalDataClassChild
 */
@DataClass(
        genConstructor = false,
        genSetters = true)
public class HierrarchicalDataClassBase implements Parcelable {

    private int mBaseData;



    // Code below generated by codegen v1.0.18.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/frameworks/base/tests/Codegen/src/com/android/codegentest/HierrarchicalDataClassBase.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    public int getBaseData() {
        return mBaseData;
    }

    @DataClass.Generated.Member
    public @android.annotation.NonNull HierrarchicalDataClassBase setBaseData( int value) {
        mBaseData = value;
        return this;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@android.annotation.NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeInt(mBaseData);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    protected HierrarchicalDataClassBase(@android.annotation.NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        int baseData = in.readInt();

        this.mBaseData = baseData;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @android.annotation.NonNull Parcelable.Creator<HierrarchicalDataClassBase> CREATOR
            = new Parcelable.Creator<HierrarchicalDataClassBase>() {
        @Override
        public HierrarchicalDataClassBase[] newArray(int size) {
            return new HierrarchicalDataClassBase[size];
        }

        @Override
        public HierrarchicalDataClassBase createFromParcel(@android.annotation.NonNull android.os.Parcel in) {
            return new HierrarchicalDataClassBase(in);
        }
    };

    @DataClass.Generated(
            time = 1603836848866L,
            codegenVersion = "1.0.18",
            sourceFile = "frameworks/base/tests/Codegen/src/com/android/codegentest/HierrarchicalDataClassBase.java",
            inputSignatures = "private  int mBaseData\nclass HierrarchicalDataClassBase extends java.lang.Object implements [android.os.Parcelable]\n@com.android.internal.util.DataClass(genConstructor=false, genSetters=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}

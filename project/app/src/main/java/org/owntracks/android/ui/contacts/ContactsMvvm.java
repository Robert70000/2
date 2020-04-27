package org.owntracks.android.ui.contacts;

import org.owntracks.android.model.FusedContact;
import org.owntracks.android.ui.base.view.MvvmView;
import org.owntracks.android.ui.base.viewmodel.MvvmViewModel;

import java.util.Collection;

/* Copyright 2016 Patrick Löwenstein
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */
public interface ContactsMvvm {

    interface View extends MvvmView {
        void removeContact(FusedContact c);
        void addContact(FusedContact c);
        void updateContact(FusedContact c);
    }

    interface ViewModel<V extends MvvmView> extends MvvmViewModel<V> {

        Collection<FusedContact> getContacts();
        void onContactClick(FusedContact contact);
    }
}

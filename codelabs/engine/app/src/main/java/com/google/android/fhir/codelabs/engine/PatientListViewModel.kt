/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.fhir.codelabs.engine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.StringFilterModifier
import com.google.android.fhir.search.search
import com.google.android.fhir.sync.Sync
import com.google.android.fhir.sync.SyncJobStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Patient

class PatientListViewModel(application: Application) : AndroidViewModel(application) {
  private val _pollState = MutableSharedFlow<SyncJobStatus>()

  val pollState: Flow<SyncJobStatus>
    get() = _pollState

  val liveSearchedPatients = MutableLiveData<List<Patient>>()

  init {
    updatePatientListAndPatientCount { getSearchResults() }
  }

  fun triggerOneTimeSync() {
    viewModelScope.launch {
      Sync.oneTimeSync<FhirSyncWorker>(getApplication())
        .shareIn(this, SharingStarted.Eagerly, 10)
        .collect { _pollState.emit(it) }
    }
  }

  /*
   Fetches patients stored locally based on the city they are in, and then updates the city field for
   each patient. Once that is complete, trigger a new sync so the changes can be uploaded.
  */
  fun triggerUpdate() {
    viewModelScope.launch {
      val fhirEngine = FhirApplication.fhirEngine(getApplication())
      val patientsFromWakefield =
        fhirEngine.search<Patient> {
          filter(
            Patient.ADDRESS_CITY,
            {
              modifier = StringFilterModifier.CONTAINS
              value = "WAKEFIELD"
            }
          )
        }

      val patientsFromTaunton =
        fhirEngine.search<Patient> {
          filter(
            Patient.ADDRESS_CITY,
            {
              modifier = StringFilterModifier.CONTAINS
              value = "TAUNTON"
            }
          )
        }

      patientsFromWakefield.forEach {
        it.address.first().city = "TAUNTON"
        fhirEngine.update(it)
      }

      patientsFromTaunton.forEach {
        it.address.first().city = "WAKEFIELD"
        fhirEngine.update(it)
      }

      triggerOneTimeSync()
    }
  }

  fun searchPatientsByName(nameQuery: String) {
    updatePatientListAndPatientCount { getSearchResults(nameQuery) }
  }

  /**
   * [updatePatientListAndPatientCount] calls the search and count lambda and updates the live data
   * values accordingly. It is initially called when this [ViewModel] is created. Later its called
   * by the client every time search query changes or data-sync is completed.
   */
  private fun updatePatientListAndPatientCount(
    search: suspend () -> List<Patient>,
  ) {
    viewModelScope.launch { liveSearchedPatients.value = search() }
  }

  private suspend fun getSearchResults(nameQuery: String = ""): List<Patient> {
    val patients: MutableList<Patient> = mutableListOf()
    FhirApplication.fhirEngine(this.getApplication())
      .search<Patient> {
        if (nameQuery.isNotEmpty()) {
          filter(
            Patient.NAME,
            {
              modifier = StringFilterModifier.CONTAINS
              value = nameQuery
            }
          )
        }
        sort(Patient.GIVEN, Order.ASCENDING)
      }
      .let { patients.addAll(it) }
    return patients
  }
}
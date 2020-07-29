// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps.data;

import java.util.HashMap;
import java.util.Map;

/** A class of state abbreviation and state name pairs. */
public class State {
  public static final Map<String, String> abbreviationToName = new HashMap<>(50);
  static {
    abbreviationToName.put("AL", "Alabama");
    abbreviationToName.put("AK", "Alaska");
    abbreviationToName.put("AZ", "Arizona");
    abbreviationToName.put("AR", "Arkansas");
    abbreviationToName.put("CA", "California");
    abbreviationToName.put("CO", "Colorado");
    abbreviationToName.put("CT", "Connecticut");
    abbreviationToName.put("DE", "Delaware");
    abbreviationToName.put("FL", "Florida");
    abbreviationToName.put("GA", "Georgia");
    abbreviationToName.put("HI", "Hawaii");
    abbreviationToName.put("ID", "Idaho");
    abbreviationToName.put("IL", "Illinois");
    abbreviationToName.put("IN", "Indiana");
    abbreviationToName.put("IA", "Iowa");
    abbreviationToName.put("KS", "Kansas");
    abbreviationToName.put("KY", "Kentucky");
    abbreviationToName.put("LA", "Louisiana");
    abbreviationToName.put("ME", "Maine");
    abbreviationToName.put("MD", "Maryland");
    abbreviationToName.put("MA", "Massachusetts");
    abbreviationToName.put("MI", "Michigan");
    abbreviationToName.put("MN", "Minnesota");
    abbreviationToName.put("MS", "Mississippi");
    abbreviationToName.put("MO", "Missouri");
    abbreviationToName.put("MT", "Montana");
    abbreviationToName.put("NE", "Nebraska");
    abbreviationToName.put("NV", "Nevada");
    abbreviationToName.put("NH", "New Hampshire");
    abbreviationToName.put("NJ", "New Jersey");
    abbreviationToName.put("NM", "New Mexico");
    abbreviationToName.put("NY", "New York");
    abbreviationToName.put("NC", "North Carolina");
    abbreviationToName.put("ND", "North Dakota");
    abbreviationToName.put("OH", "Ohio");
    abbreviationToName.put("OK", "Oklahoma");
    abbreviationToName.put("OR", "Oregon");
    abbreviationToName.put("PA", "Pennsylvania");
    abbreviationToName.put("RI", "Rhode Island");
    abbreviationToName.put("SC", "South Carolina");
    abbreviationToName.put("SD", "South Dakota");
    abbreviationToName.put("TN", "Tennessee");
    abbreviationToName.put("TX", "Texas");
    abbreviationToName.put("UT", "Utah");
    abbreviationToName.put("VT", "Vermont");
    abbreviationToName.put("VA", "Virginia");
    abbreviationToName.put("WA", "Washington");
    abbreviationToName.put("WV", "West Virginia");
    abbreviationToName.put("WI", "Wisconsin");
    abbreviationToName.put("WY", "Wyoming");
  };
}

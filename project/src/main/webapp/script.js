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

// use modern JavaScript (ES5)
'use strict';

/**
 * Adds a brief version of the official election/candidate information to the
 * homepage.
 * Queries the backend database with the user input of address.
 *
 * Each election has the following HTML DOM structure:
 *   <div id="election-i">
 *     <h3>Election name</h3>
 *     <time>Election date</time>
 *     <div id="election-1-positions">
 *       <ul>
 *         <li>
 *           <h4>Position 1</h4>
 *           <p>Table of Candidates</p>
 *           <table class="candidates-table">
 *             <tr>
 *               <td><a href="URL">Candidate Name</a></th>
 *               <td>Party Affiliation</th>
 *               <td>Incumbent</th>
 *             </tr>
 *           </table>
 *         </li>
 *         <li>
 *           <h4>Position 2...</h4>
 *         </li>
 *       </ul>
 *     </div>
 *     <hr>
 *   </div>
 */
async function addBriefElectionCandidateInformation() {
  const address = location.search.substring(location.search.indexOf('=') + 1,
      location.search.indexOf('&'));
  const listAllElections =
      location.search.substring(location.search.lastIndexOf('=') + 1);

  // Send GET request to /data with address and whether to list all elections.
  const response = await fetch(`/data?address=${address}&listAllElections=${listAllElections}`);
  const dataPackage = await response.json();
  const elections = dataPackage.electionsData;
  const alert = dataPackage.alert;

  // Add (brief version) official election/candidate information to HTML.
  const electionsContainer = document.getElementById('elections-container');
  electionsContainer.innerHTML = '';
  for (let electionIndex = 0; electionIndex < elections.length;
    electionIndex++) {
    const electionElement = constructElection(elections[electionIndex],
        electionIndex);
    electionsContainer.appendChild(electionElement);
  }
  // Set alert to the user if necessary.
  if (alert) {
    document.getElementById('alert').innerText = alert;
  }
}

/**
 * Constructs the HTML of one election, which contains a list of positions.
 */
function constructElection(election, electionIndex) {
  const electionElement = document.createElement('div');
  electionElement.id = `election-${electionIndex + 1}`;
  electionElement.innerHTML =
      `<h3>${election.electionName}</h3>
        <time>${election.date}</time>`;
  const positions = election.positions;
  const positionsElement = document.createElement('div');
  positionsElement.id = electionElement.id + `-positions`;
  const positionsList =
      constructPositionsList(positions, election.electionName);
  positionsElement.appendChild(positionsList);
  electionElement.appendChild(positionsElement);
  return electionElement;
}

/**
 * Constructs the HTML of a list of positions, corresponding to one election.
 */
function constructPositionsList(positions, electionName) {
  const positionsList = document.createElement('ul');
  for (let positionIndex = 0; positionIndex < positions.length;
    positionIndex++) {
    const positionItem = constructPositionsListItem(positions[positionIndex],
        positionIndex, electionName);
    positionsList.appendChild(positionItem);
  }
  return positionsList;
}

/**
 * Constructs the HTML of one position, which contains a table of candidates.
 */
function constructPositionsListItem(position, positionIndex, electionName) {
  const positionItem = document.createElement('li');
  positionItem.innerHTML =
      `<h4>Position ${positionIndex + 1}: ${position.positionName}</h4>
        <p>Table of Candidates</p>`;
  const candidatesTable =
      constructCandidateTable(position.candidates, electionName);
  positionItem.appendChild(candidatesTable);
  return positionItem;
}

/**
 * Constructs the HTML of a table of candidates, corresponding to one position.
 */
function constructCandidateTable(candidates, electionName) {
  const candidatesTable = document.createElement('table');
  candidatesTable.innerHTML =
      `<tr>
         <th>Candidate Name</th>
         <th>Party Affiliation</th>
         <th>Incumbent</th>
       </tr>`;
  for (let candidateIndex = 0; candidateIndex < candidates.length;
    candidateIndex++) {
    const candidate = candidates[candidateIndex];
    candidatesTable.innerHTML +=
        constructCandidateTableRow(candidate.id,
            candidate.candidateName,
            candidate.partyAffiliation,
            candidate.isIncumbent,
            electionName);
  }
  return candidatesTable;
}

/**
 * Constructs the HTML of one table row for a candidate. Adds candidate info
 * and embed candidate ID into the URL.
 */
function constructCandidateTableRow(
    id, candidateName, partyAffiliation, isIncumbent, electionName) {
  return `<tr>
            <td><a href=
                "candidate.html?candidateId=${id}&electionName=${electionName}">
                    ${candidateName}</a></td>
            <td>${partyAffiliation}</td>
            <td>${isIncumbent ? 'Yes' : 'No'}</td>
          </tr>`;
}

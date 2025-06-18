package at.aau.serg.monopoly.firebase;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserStatisticsServiceTest {

    private UserStatisticsService userStatisticsService;
    private Firestore firestore;

    private static MockedStatic<FirestoreClient> firestoreClientMock;

    @BeforeAll
    static void initStaticMock() {
        firestoreClientMock = Mockito.mockStatic(FirestoreClient.class);
    }

    @AfterAll
    static void closeStaticMock() {
        firestoreClientMock.close();
    }

    @BeforeEach
    void setup() {
        firestore = mock(Firestore.class);
        userStatisticsService = new UserStatisticsService();
        firestoreClientMock.when(FirestoreClient::getFirestore).thenReturn(firestore);
    }

    @Test
    void testUpdateUserStats_withValidGames() throws Exception {
        CollectionReference users = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference history = mock(CollectionReference.class);
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
        QuerySnapshot snapshot = mock(QuerySnapshot.class);
        QueryDocumentSnapshot doc1 = mock(QueryDocumentSnapshot.class);
        QueryDocumentSnapshot doc2 = mock(QueryDocumentSnapshot.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);

        when(firestore.collection("users")).thenReturn(users);
        when(users.document("uid")).thenReturn(userDoc);
        when(userDoc.collection("gameHistory")).thenReturn(history);
        when(history.get()).thenReturn(future);
        when(future.get()).thenReturn(snapshot);
        when(snapshot.getDocuments()).thenReturn(List.of(doc1, doc2));
        when(doc1.getData()).thenReturn(Map.of("won", true, "endMoney", 2000));
        when(doc2.getData()).thenReturn(Map.of("won", false, "endMoney", 1500));

        when(userDoc.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.contains("name")).thenReturn(true);
        when(userSnapshot.getString("name")).thenReturn("TestUser");

        userStatisticsService.updateUserStats("uid", firestore);

        verify(userDoc).set(argThat((Map<String, Object> map) ->
                map.get("wins").equals(1) &&
                        map.get("highestMoney").equals(2000) &&
                        map.get("averageMoney").equals(1750) &&
                        map.get("gamesPlayed").equals(2) &&
                        map.get("level").equals(1) &&
                        map.get("name").equals("TestUser")
        ), any(SetOptions.class));
    }

    @Test
    void testUpdateUserStatsEdgeCase_emptyHistory() throws Exception {
        CollectionReference users = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference history = mock(CollectionReference.class);
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
        QuerySnapshot snapshot = mock(QuerySnapshot.class);

        when(firestore.collection("users")).thenReturn(users);
        when(users.document("uid")).thenReturn(userDoc);
        when(userDoc.collection("gameHistory")).thenReturn(history);
        when(history.get()).thenReturn(future);
        when(future.get()).thenReturn(snapshot);
        when(snapshot.getDocuments()).thenReturn(Collections.emptyList());

        userStatisticsService.updateUserStats("uid", firestore);
        verify(userDoc, never()).set(anyMap(), any(SetOptions.class));
    }

    @Test
    void testUpdateStatsForUsers_firestoreIsNull() {
        firestoreClientMock.when(FirestoreClient::getFirestore).thenReturn(null);
        userStatisticsService.updateStatsForUsers(List.of("uid"));
        // Kein Exception = Test besteht
    }

    @Test
    void testUpdateUserStats_firestoreThrowsException() throws Exception {
        CollectionReference users = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference history = mock(CollectionReference.class);
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);

        when(firestore.collection("users")).thenReturn(users);
        when(users.document("uid")).thenReturn(userDoc);
        when(userDoc.collection("gameHistory")).thenReturn(history);
        when(history.get()).thenReturn(future);
        when(future.get()).thenThrow(new ExecutionException(new RuntimeException("Test Exception")));

        userStatisticsService.updateUserStats("uid", firestore);

        // Kein Crash = Test besteht
    }

    @Test
    void testUpdateUserStats_gameDataNull() throws Exception {
        CollectionReference users = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference history = mock(CollectionReference.class);
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
        QuerySnapshot snapshot = mock(QuerySnapshot.class);
        QueryDocumentSnapshot doc1 = mock(QueryDocumentSnapshot.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);

        when(firestore.collection("users")).thenReturn(users);
        when(users.document("uid")).thenReturn(userDoc);
        when(userDoc.collection("gameHistory")).thenReturn(history);
        when(history.get()).thenReturn(future);
        when(future.get()).thenReturn(snapshot);
        when(snapshot.getDocuments()).thenReturn(List.of(doc1));
        when(doc1.getData()).thenReturn(null);

        when(userDoc.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(false); // Name wird nicht gesetzt

        userStatisticsService.updateUserStats("uid", firestore);

        verify(userDoc).set(argThat((Map<String, Object> map) ->
                map.get("wins").equals(0) &&
                        map.get("highestMoney").equals(0) &&
                        map.get("averageMoney").equals(0) &&
                        map.get("gamesPlayed").equals(1) &&
                        map.get("level").equals(0) &&
                        !map.containsKey("name")
        ), any(SetOptions.class));
    }

    @Test
    void testUpdateUserStats_gameWithoutEndMoney() throws Exception {
        CollectionReference users = mock(CollectionReference.class);
        DocumentReference userDoc = mock(DocumentReference.class);
        CollectionReference history = mock(CollectionReference.class);
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
        QuerySnapshot snapshot = mock(QuerySnapshot.class);
        QueryDocumentSnapshot doc1 = mock(QueryDocumentSnapshot.class);
        DocumentSnapshot userSnapshot = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> userFuture = mock(ApiFuture.class);

        when(firestore.collection("users")).thenReturn(users);
        when(users.document("uid")).thenReturn(userDoc);
        when(userDoc.collection("gameHistory")).thenReturn(history);
        when(history.get()).thenReturn(future);
        when(future.get()).thenReturn(snapshot);
        when(snapshot.getDocuments()).thenReturn(List.of(doc1));
        when(doc1.getData()).thenReturn(Map.of("won", true)); // Kein endMoney

        when(userDoc.get()).thenReturn(userFuture);
        when(userFuture.get()).thenReturn(userSnapshot);
        when(userSnapshot.exists()).thenReturn(false);

        userStatisticsService.updateUserStats("uid", firestore);

        verify(userDoc).set(argThat((Map<String, Object> map) ->
                map.get("wins").equals(1) &&
                        map.get("highestMoney").equals(0) &&
                        map.get("averageMoney").equals(0) &&
                        map.get("gamesPlayed").equals(1)
        ), any(SetOptions.class));
    }
}
